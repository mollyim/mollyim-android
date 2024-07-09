/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDexApplication;

import com.bumptech.glide.Glide;
import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.ConscryptSignal;
import org.greenrobot.eventbus.EventBus;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.signal.core.util.MemoryTracker;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.AndroidLogger;
import org.signal.core.util.logging.Log;
import org.signal.core.util.logging.Scrubber;
import org.signal.core.util.tracing.Tracer;
import org.signal.glide.SignalGlideCodecs;
import org.signal.libsignal.protocol.logging.SignalProtocolLoggerProvider;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener;
import org.thoughtcrime.securesms.avatar.AvatarPickerStorage;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.emoji.JumboEmoji;
import org.thoughtcrime.securesms.gcm.FcmFetchManager;
import org.thoughtcrime.securesms.jobs.AccountConsistencyWorkerJob;
import org.thoughtcrime.securesms.jobs.CheckServiceReachabilityJob;
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob;
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.FontDownloaderJob;
import org.thoughtcrime.securesms.jobs.GroupRingCleanupJob;
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.PnpInitializeDevicesJob;
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshSvrCredentialsJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.RetrieveRemoteAnnouncementsJob;
import org.thoughtcrime.securesms.jobs.StoryOnboardingDownloadJob;
import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger;
import org.thoughtcrime.securesms.logging.PersistentLogger;
import org.thoughtcrime.securesms.messageprocessingalarm.RoutineMessageFetchReceiver;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.mms.SignalGlideComponents;
import org.thoughtcrime.securesms.mms.SignalGlideModule;
import org.thoughtcrime.securesms.net.NetworkManager;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.ratelimit.RateLimitUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.ringrtc.RingRtcLogger;
import org.thoughtcrime.securesms.service.AnalyzeDatabaseAlarmListener;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.service.MessageBackupListener;
import org.thoughtcrime.securesms.service.RotateSenderCertificateListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.service.WipeMemoryService;
import org.thoughtcrime.securesms.service.webrtc.ActiveCallManager;
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.SignalUncaughtExceptionHandler;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import rxdogtag2.RxDogTag;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(ApplicationContext.class);

  private static ApplicationContext instance;

  public ApplicationContext() {
    super();
    instance = this;
  }

  public static @NonNull ApplicationContext getInstance() {
    return instance;
  }

  private volatile boolean isAppInitialized;

  @Override
  public void onCreate() {
    initializeLogging(true);
    Log.i(TAG, "onCreate()");

    super.onCreate();

    SqlCipherLibraryLoader.load();
    EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus();
    DynamicTheme.setDefaultDayNightMode(this);
    ScreenLockController.enableAutoLock(TextSecurePreferences.isBiometricScreenLockEnabled(this));

    initializePassphraseLock();
    cleanCacheDir();
  }

  private void onCreateUnlock() {
    Tracer.getInstance().start("Application#onCreateUnlock()");
    AppStartup.getInstance().onApplicationCreate();
    SignalLocalMetrics.ColdStart.start();

    long startTime = System.currentTimeMillis();

    if (FeatureFlags.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }

    AppStartup.getInstance().addBlocking("sqlcipher-init", () -> {
                              SignalDatabase.init(this,
                                                  DatabaseSecretProvider.getOrCreateDatabaseSecret(this),
                                                  AttachmentSecretProvider.getInstance(this).getOrCreateAttachmentSecret());
                            })
                            .addBlocking("logging", () -> {
                              initializeLogging(false);
                              Log.i(TAG, "onCreateUnlock()");
                            })
                            .addBlocking("security-provider", this::initializeSecurityProvider)
                            .addBlocking("crash-handling", this::initializeCrashHandling)
                            .addBlocking("rx-init", this::initializeRx)
                            .addBlocking("app-dependencies", this::initializeAppDependencies)
                            .addBlocking("scrubber", () -> Scrubber.setIdentifierHmacKeyProvider(() -> SignalStore.svr().getOrCreateMasterKey().deriveLoggingKey()))
                            .addBlocking("network-settings", this::initializeNetworkSettings)
                            .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
                            .addBlocking("gcm-check", this::initializeFcmCheck)
                            .addBlocking("app-migrations", this::initializeApplicationMigrations)
                            .addBlocking("lifecycle-observer", () -> AppDependencies.getAppForegroundObserver().addListener(this))
                            .addBlocking("message-retriever", this::initializeMessageRetrieval)
                            .addBlocking("blob-provider", this::initializeBlobProvider)
                            .addBlocking("feature-flags", FeatureFlags::init)
                            .addBlocking("ring-rtc", this::initializeRingRtc)
                            .addBlocking("glide", () -> SignalGlideModule.setRegisterGlideComponents(new SignalGlideComponents()))
                            .addNonBlocking(() -> RegistrationUtil.maybeMarkRegistrationComplete())
                            .addNonBlocking(() -> Glide.get(this))
                            .addNonBlocking(this::cleanAvatarStorage)
                            .addNonBlocking(this::initializeRevealableMessageManager)
                            .addNonBlocking(this::initializePendingRetryReceiptManager)
                            .addNonBlocking(this::initializeScheduledMessageManager)
                            .addNonBlocking(PreKeysSyncJob::enqueueIfNeeded)
                            .addNonBlocking(this::initializePeriodicTasks)
                            .addNonBlocking(this::initializeCircumvention)
                            .addNonBlocking(this::initializeCleanup)
                            .addNonBlocking(this::initializeGlideCodecs)
                            .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
                            .addNonBlocking(this::beginJobLoop)
                            .addNonBlocking(EmojiSource::refresh)
                            .addNonBlocking(() -> AppDependencies.getGiphyMp4Cache().onAppStart(this))
                            .addNonBlocking(this::ensureProfileUploaded)
                            .addNonBlocking(() -> AppDependencies.getExpireStoriesManager().scheduleIfNecessary())
                            .addPostRender(() -> AppDependencies.getDeletedCallEventManager().scheduleIfNecessary())
                            .addPostRender(() -> RateLimitUtil.retryAllRateLimitedMessages(this))
                            .addPostRender(this::initializeExpiringMessageManager)
                            .addPostRender(this::initializeTrimThreadsByDateManager)
                            .addPostRender(RefreshSvrCredentialsJob::enqueueIfNecessary)
                            .addPostRender(() -> DownloadLatestEmojiDataJob.scheduleIfNecessary(this))
                            .addPostRender(EmojiSearchIndexDownloadJob::scheduleIfNecessary)
                            .addPostRender(() -> SignalDatabase.messageLog().trimOldMessages(System.currentTimeMillis(), FeatureFlags.retryRespondMaxAge()))
                            .addPostRender(() -> JumboEmoji.updateCurrentVersion(this))
                            .addPostRender(RetrieveRemoteAnnouncementsJob::enqueue)
                            .addPostRender(() -> AndroidTelecomUtil.registerPhoneAccount())
                            .addPostRender(() -> AppDependencies.getJobManager().add(new FontDownloaderJob()))
                            .addPostRender(CheckServiceReachabilityJob::enqueueIfNecessary)
                            .addPostRender(GroupV2UpdateSelfProfileKeyJob::enqueueForGroupsIfNecessary)
                            .addPostRender(StoryOnboardingDownloadJob.Companion::enqueueIfNeeded)
                            .addPostRender(PnpInitializeDevicesJob::enqueueIfNecessary)
                            .addPostRender(() -> AppDependencies.getExoPlayerPool().getPoolStats().getMaxUnreserved())
                            .addPostRender(() -> AppDependencies.getRecipientCache().warmUp())
                            .addPostRender(AccountConsistencyWorkerJob::enqueueIfNecessary)
                            .addPostRender(GroupRingCleanupJob::enqueue)
                            .addPostRender(LinkedDeviceInactiveCheckJob::enqueueIfNecessary)
                            .addPostRender(() -> ActiveCallManager.clearNotifications(this))
                            .execute();

    Log.d(TAG, "onCreateUnlock() took " + (System.currentTimeMillis() - startTime) + " ms");
    SignalLocalMetrics.ColdStart.onApplicationCreateFinished();
    Tracer.getInstance().end("Application#onCreateUnlock()");
  }

  @Override
  public void onForeground() {
    Log.i(TAG, "App is now visible.");

    if (!KeyCachingService.isLocked()) {
      onStartUnlock();
    }
  }

  private void onStartUnlock() {
    long startTime = System.currentTimeMillis();

    AppDependencies.getFrameRateTracker().start();
    AppDependencies.getMegaphoneRepository().onAppForegrounded();
    AppDependencies.getDeadlockDetector().start();
    FcmFetchManager.onForeground(this);

    SignalExecutors.BOUNDED.execute(() -> {
      FeatureFlags.refreshIfNecessary();
      RetrieveProfileJob.enqueueRoutineFetchIfNecessary();
      executePendingContactSync();
      checkBuildExpiration();
      MemoryTracker.start();

      long lastForegroundTime = SignalStore.misc().getLastForegroundTime();
      long currentTime        = System.currentTimeMillis();
      long timeDiff           = currentTime - lastForegroundTime;

      if (timeDiff < 0) {
        Log.w(TAG, "Time travel! The system clock has moved backwards. (currentTime: " + currentTime + " ms, lastForegroundTime: " + lastForegroundTime + " ms, diff: " + timeDiff + " ms)");
      }

      SignalStore.misc().setLastForegroundTime(currentTime);
    });

    Log.d(TAG, "onStartUnlock() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onBackground() {
    Log.i(TAG, "App is no longer visible.");

    ScreenLockController.onAppBackgrounded(this);
    if (!KeyCachingService.isLocked()) {
      onStopUnlock();
    }
  }

  private void onStopUnlock() {
    AppDependencies.getMessageNotifier().clearVisibleThread();
    AppDependencies.getFrameRateTracker().stop();
    AppDependencies.getDeadlockDetector().stop();
    MemoryTracker.stop();
  }

  @MainThread
  public void onUnlock() {
    Log.i(TAG, "onUnlock()");

    if (!isAppInitialized) {
      onCreateUnlock();
      registerKeyEventReceiver();
      onStartUnlock();
      isAppInitialized = true;
    }
  }

  @MainThread
  public void onLock(boolean keyExpired) {
    Log.i(TAG, "onLock()");

    ActiveCallManager.stop();

    finalizeExpiringMessageManager();
    finalizeMessageRetrieval();
    unregisterKeyEventReceiver();

    MessageNotifier messageNotifier = AppDependencies.getMessageNotifier();
    messageNotifier.cancelDelayedNotifications();
    boolean hadActiveNotifications = messageNotifier.clearNotifications(this);

    if (hadActiveNotifications && keyExpired && SignalStore.account().isPushAvailable() &&
        TextSecurePreferences.isPassphraseLockNotificationsEnabled(this) ) {
      Log.d(TAG, "Replacing active notifications with may-have-messages notification");
      FcmFetchManager.postMayHaveMessagesNotification(this);
    }

    ThreadUtil.runOnMainDelayed(() -> {
      AppDependencies.getJobManager().shutdown(TimeUnit.SECONDS.toMillis(10));
      KeyCachingService.clearMasterSecret();
      WipeMemoryService.run(this, true);
    }, TimeUnit.SECONDS.toMillis(1));
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry() <= 0 && !SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build expired!");
      SignalStore.misc().setClientDeprecated(true);
    }
  }

  private void initializeSecurityProvider() {
    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(ConscryptSignal.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  @VisibleForTesting
  protected void initializeLogging(boolean locked) {
    if (locked) {
      Log.initialize(AndroidLogger.INSTANCE);
    } else {
      boolean enableLogging = TextSecurePreferences.isLogEnabled(this);
      boolean alwaysRedact  = !BuildConfig.DEBUG;
      Log.configure(FeatureFlags::internalUser, enableLogging, alwaysRedact, AndroidLogger.INSTANCE, new PersistentLogger(this));

      SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());

      SignalExecutors.UNBOUNDED.execute(() -> {
        Log.blockUntilAllWritesFinished();
        LogDatabase.getInstance(this).logs().trimToSize();
        LogDatabase.getInstance(this).crashes().trimToSize();
      });
    }
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeRx() {
    RxDogTag.install();
    RxJavaPlugins.setInitIoSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED_IO, true, false));
    RxJavaPlugins.setInitComputationSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED, true, false));
    RxJavaPlugins.setErrorHandler(e -> {
      boolean wasWrapped = false;
      while ((e instanceof UndeliverableException || e instanceof AssertionError || e instanceof OnErrorNotImplementedException) && e.getCause() != null) {
        wasWrapped = true;
        e = e.getCause();
      }

      if (wasWrapped && (e instanceof SocketException || e instanceof SocketTimeoutException || e instanceof InterruptedException)) {
        return;
      }

      Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
      if (uncaughtExceptionHandler == null) {
        uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
      }

      uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), e);
    });
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, AppDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    AppDependencies.getIncomingMessageObserver();
  }

  public void finalizeMessageRetrieval() {
    AppDependencies.closeConnections();
  }

  private void initializePassphraseLock() {
    if (MasterSecretUtil.isPassphraseInitialized(this)) {
      try {
        KeyCachingService.setMasterSecret(MasterSecretUtil.getMasterSecret(this,
                MasterSecretUtil.getUnencryptedPassphrase()));
        TextSecurePreferences.setPassphraseLockEnabled(this, false);
        onUnlock();
      } catch (InvalidPassphraseException | UnrecoverableKeyException e) {
        TextSecurePreferences.setPassphraseLockEnabled(this, true);
      }
    }
  }

  private void cleanCacheDir() {
    SignalExecutors.BOUNDED.execute(() -> {
      if (BuildConfig.USE_OSM) {
        FileUtils.deleteDirectoryContents(StorageUtil.getTileCacheDirectory(this));
      }
    });
  }

  @VisibleForTesting
  void initializeAppDependencies() {
    AppDependencies.init(this, new ApplicationDependencyProvider(this));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      Log.i(TAG, "First ever app launch!");
      AppInitialization.onFirstEverAppLaunch(this);

      Log.i(TAG, "Generating new identity keys...");
      SignalStore.account().generateAciIdentityKeyIfNecessary();
      SignalStore.account().generatePniIdentityKeyIfNecessary();

      Log.i(TAG, "Setting first install version to " + Util.getSignalCanonicalVersionCode());
      TextSecurePreferences.setFirstInstallVersion(this, Util.getSignalCanonicalVersionCode());
    }
  }

  private void initializeNetworkSettings() {
    NetworkManager nm = AppDependencies.getNetworkManager();

    nm.setProxyChoice(TextSecurePreferences.getProxyType(this));
    nm.setProxySocksHost(TextSecurePreferences.getProxySocksHost(this));
    nm.setProxySocksPort(TextSecurePreferences.getProxySocksPort(this));
    nm.applyProxyConfig();

    if (TextSecurePreferences.getFirstInstallVersion(this) != -1) {
      nm.setNetworkEnabled(TextSecurePreferences.hasSeenNetworkConfig(this));
    } else {
      Log.i(TAG, "Network will be disabled until registration begins");
      TextSecurePreferences.setHasSeenNetworkConfig(this, false);
    }
  }

  private void initializeFcmCheck() {
    if (!SignalStore.account().isRegistered()) {
      return;
    }

    PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(this);

    if (fcmStatus == PlayServicesUtil.PlayServicesStatus.DISABLED) {
      if (SignalStore.account().isFcmEnabled()) {
        Log.i(TAG, "Play Services are disabled. Disabling FCM.");
        SignalStore.account().setFcmEnabled(false);
        SignalStore.account().setFcmToken(null);
        SignalStore.account().setFcmTokenLastSetTime(-1);
        AppDependencies.getJobManager().add(new RefreshAttributesJob());
      } else {
        SignalStore.account().setFcmTokenLastSetTime(-1);
      }
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS &&
               !SignalStore.account().isFcmEnabled() &&
               SignalStore.account().getFcmTokenLastSetTime() < 0) {
      Log.i(TAG, "Play Services are newly-available. Updating to use FCM.");
      SignalStore.account().setFcmEnabled(true);
      AppDependencies.getJobManager().startChain(new FcmRefreshJob())
                                             .then(new RefreshAttributesJob())
                                             .enqueue();
    } else {
      long lastSetTime = SignalStore.account().getFcmTokenLastSetTime();
      long nextSetTime = lastSetTime + TimeUnit.HOURS.toMillis(6);
      long now         = System.currentTimeMillis();

      if (SignalStore.account().getFcmToken() == null || nextSetTime <= now || lastSetTime > now) {
        AppDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeExpiringMessageManager() {
    AppDependencies.getExpiringMessageManager().checkSchedule();
  }

  private void finalizeExpiringMessageManager() {
    AppDependencies.getExpiringMessageManager().quit();
  }

  private void initializeRevealableMessageManager() {
    AppDependencies.getViewOnceMessageManager().scheduleIfNecessary();
  }

  private void initializePendingRetryReceiptManager() {
    AppDependencies.getPendingRetryReceiptManager().scheduleIfNecessary();
  }

  private void initializeScheduledMessageManager() {
    AppDependencies.getScheduledMessageManager().scheduleIfNecessary();
  }

  private void initializeTrimThreadsByDateManager() {
    KeepMessagesDuration keepMessagesDuration = SignalStore.settings().getKeepMessagesDuration();
    if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
      AppDependencies.getTrimThreadsByDateManager().scheduleIfNecessary();
    }
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    MessageBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    RoutineMessageFetchReceiver.startOrUpdateAlarm(this);
    AnalyzeDatabaseAlarmListener.schedule(this);

    if (TextSecurePreferences.isUpdateApkEnabled(this)) {
      ApkUpdateRefreshListener.scheduleIfAllowed(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Map<String, String> fieldTrials = new HashMap<>();
      if (FeatureFlags.callingFieldTrialAnyAddressPortsKillSwitch()) {
        fieldTrials.put("RingRTC-AnyAddressPortsKillSwitch", "Enabled");
      }
      if (!SignalStore.internalValues().callingDisableLBRed()) {
        fieldTrials.put("RingRTC-Audio-LBRed-For-Opus", "Enabled,bitrate_pri:22000");
      }
      CallManager.initialize(this, new RingRtcLogger(), fieldTrials);
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @WorkerThread
  private void initializeCircumvention() {
    if (AppDependencies.getSignalServiceNetworkAccess().isCensored()) {
      try {
        ProviderInstaller.installIfNeeded(ApplicationContext.this);
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }
  }

  private void ensureProfileUploaded() {
    if (SignalStore.account().isRegistered()) {
      if (SignalStore.registrationValues().needDownloadProfileOrAvatar()) {
        Log.w(TAG, "User has linked a device, but has not yet downloaded the profile. Downloading now.");
        AppDependencies.getJobManager().add(new RefreshOwnProfileJob());
      } else if (!SignalStore.registrationValues().hasUploadedProfile() && !Recipient.self().getProfileName().isEmpty()) {
        Log.w(TAG, "User has a profile, but has not uploaded one. Uploading now.");
        AppDependencies.getJobManager().add(new ProfileUploadJob());
      }
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      AppDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  @VisibleForTesting
  protected void beginJobLoop() {
    AppDependencies.getJobManager().beginJobLoop();
  }

  @WorkerThread
  private void initializeBlobProvider() {
    BlobProvider.getInstance().initialize(this);
  }

  @WorkerThread
  private void cleanAvatarStorage() {
    AvatarPickerStorage.cleanOrphans(this);
  }

  @WorkerThread
  private void initializeCleanup() {
    int deleted = SignalDatabase.attachments().deleteAbandonedPreuploadedAttachments();
    Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
  }

  private void initializeGlideCodecs() {
    SignalGlideCodecs.setLogProvider(new org.signal.glide.Log.Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) {
        Log.v(tag, message);
      }

      @Override
      public void d(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
      }

      @Override
      public void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
      }

      @Override
      public void w(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
      }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
      }
    });
  }

  private final BroadcastReceiver keyEventReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      boolean keyExpired = intent.getBooleanExtra(KeyCachingService.EXTRA_KEY_EXPIRED, false);
      onLock(keyExpired);
    }
  };

  private void registerKeyEventReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(KeyCachingService.CLEAR_KEY_EVENT);
    ContextCompat.registerReceiver(this, keyEventReceiver, filter, KeyCachingService.KEY_PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  private void unregisterKeyEventReceiver() {
    unregisterReceiver(keyEventReceiver);
  }

  @Override
  protected void attachBaseContext(Context base) {
    DynamicLanguageContextWrapper.updateContext(base);
    super.attachBaseContext(base);
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}
