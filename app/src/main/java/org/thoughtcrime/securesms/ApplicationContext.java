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
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
import org.signal.glide.SignalGlideCodecs;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.avatar.AvatarPickerStorage;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.gcm.FcmJobService;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob;
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.GroupV1MigrationJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger;
import org.thoughtcrime.securesms.logging.PersistentLogger;
import org.thoughtcrime.securesms.messageprocessingalarm.MessageProcessReceiver;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.net.NetworkManager;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.ratelimit.RateLimitUtil;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.ringrtc.RingRtcLogger;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.service.WipeMemoryService;
import org.thoughtcrime.securesms.service.RotateSenderCertificateListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener;
import org.thoughtcrime.securesms.service.webrtc.WebRtcCallService;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.SignalUncaughtExceptionHandler;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

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

  private volatile boolean isAppInitialized;

  public ApplicationContext() {
    super();
    instance = this;
  }

  public static @NonNull ApplicationContext getInstance(Context context) {
    return (ApplicationContext) context.getApplicationContext();
  }

  public static @NonNull ApplicationContext getInstance() {
    return instance;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");

    super.onCreate();

    initializeSecurityProvider();
    SqlCipherLibraryLoader.load(this);
    if (Build.VERSION.SDK_INT < 21) {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
    DynamicTheme.setDefaultDayNightMode(this);

    initializePassphraseLock();
  }

  private void onCreateUnlock() {
    Tracer.getInstance().start("Application#onCreate()");
    AppStartup.getInstance().onApplicationCreate();

    long startTime = System.currentTimeMillis();

    if (FeatureFlags.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }

    AppStartup.getInstance().addBlocking("logging", () -> {
                                initializeLogging();
                                Log.i(TAG, "onCreateUnlock()");
                            })
                            .addBlocking("crash-handling", this::initializeCrashHandling)
                            .addBlocking("rx-init", () -> {
                              RxJavaPlugins.setInitIoSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED_IO, true, false));
                              RxJavaPlugins.setInitComputationSchedulerHandler(schedulerSupplier -> Schedulers.from(SignalExecutors.BOUNDED, true, false));
                            })
                            .addBlocking("app-dependencies", this::initializeAppDependencies)
                            .addBlocking("notification-channels", () -> NotificationChannels.create(this))
                            .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
                            .addBlocking("network-settings", this::initializeNetworkSettings)
                            .addBlocking("gcm-check", this::initializeGcmCheck)
                            .addBlocking("app-migrations", this::initializeApplicationMigrations)
                            .addBlocking("ring-rtc", this::initializeRingRtc)
                            .addBlocking("mark-registration", () -> RegistrationUtil.maybeMarkRegistrationComplete(this))
                            .addBlocking("lifecycle-observer", () -> ApplicationDependencies.getAppForegroundObserver().addListener(this))
                            .addBlocking("message-retriever", this::initializeMessageRetrieval)
                            .addBlocking("blob-provider", this::initializeBlobProvider)
                            .addBlocking("feature-flags", FeatureFlags::init)
                            .addNonBlocking(this::cleanAvatarStorage)
                            .addNonBlocking(this::initializeRevealableMessageManager)
                            .addNonBlocking(this::initializePendingRetryReceiptManager)
                            .addNonBlocking(this::initializeSignedPreKeyCheck)
                            .addNonBlocking(this::initializePeriodicTasks)
                            .addNonBlocking(this::initializeCircumvention)
                            .addNonBlocking(this::initializePendingMessages)
                            .addNonBlocking(this::initializeCleanup)
                            .addNonBlocking(this::initializeGlideCodecs)
                            .addNonBlocking(RefreshPreKeysJob::scheduleIfNecessary)
                            .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
                            .addNonBlocking(() -> ApplicationDependencies.getJobManager().beginJobLoop())
                            .addNonBlocking(EmojiSource::refresh)
                            .addPostRender(() -> RateLimitUtil.retryAllRateLimitedMessages(this))
                            .addPostRender(this::initializeExpiringMessageManager)
                            .addPostRender(() -> SignalStore.settings().setDefaultSms(Util.isDefaultSmsProvider(this)))
                            .addPostRender(() -> DownloadLatestEmojiDataJob.scheduleIfNecessary(this))
                            .addPostRender(EmojiSearchIndexDownloadJob::scheduleIfNecessary)
                            .addPostRender(() -> DatabaseFactory.getMessageLogDatabase(this).trimOldMessages(System.currentTimeMillis(), FeatureFlags.retryRespondMaxAge()))
                            .execute();

    Log.d(TAG, "onCreateUnlock() took " + (System.currentTimeMillis() - startTime) + " ms");
    Tracer.getInstance().end("Application#onCreate()");
  }

  @Override
  public void onForeground() {
    long startTime = System.currentTimeMillis();
    Log.i(TAG, "App is now visible.");

    if (!KeyCachingService.isLocked()) {
      onStartUnlock();
    }

    Log.d(TAG, "onStart() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  private void onStartUnlock() {
    ApplicationDependencies.getFrameRateTracker().begin();
    ApplicationDependencies.getMegaphoneRepository().onAppForegrounded();

    SignalExecutors.BOUNDED.execute(() -> {
      FeatureFlags.refreshIfNecessary();
      ApplicationDependencies.getRecipientCache().warmUp();
      RetrieveProfileJob.enqueueRoutineFetchIfNecessary(this);
      GroupV1MigrationJob.enqueueRoutineMigrationsIfNecessary(this);
      executePendingContactSync();
      ApplicationDependencies.getShakeToReport().enable();
      checkBuildExpiration();
    });
  }

  @Override
  public void onBackground() {
    Log.i(TAG, "App is no longer visible.");
    if (!KeyCachingService.isLocked()) {
      onStopUnlock();
    }
  }

  private void onStopUnlock() {
    ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    ApplicationDependencies.getFrameRateTracker().end();
    ApplicationDependencies.getShakeToReport().disable();
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
  public void onLock() {
    Log.i(TAG, "onLock()");

    stopService(new Intent(this, WebRtcCallService.class));

    finalizeExpiringMessageManager();
    finalizeMessageRetrieval();
    unregisterKeyEventReceiver();

    ThreadUtil.runOnMainDelayed(() -> {
      ApplicationDependencies.getJobManager().shutdown(TimeUnit.SECONDS.toMillis(10));
      KeyCachingService.clearMasterSecret();
      WipeMemoryService.run(this, true);
    }, TimeUnit.SECONDS.toMillis(1));
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry() <= 0 && !SignalStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build expired!");
      SignalStore.misc().markClientDeprecated();
    }
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    PersistentLogger persistentLogger = new PersistentLogger(this);
    Log.setInternalCheck(FeatureFlags::internalUser);
    Log.setPersistentLogger(persistentLogger);
    Log.setLogging(TextSecurePreferences.isLogEnabled(this));

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());

    SignalExecutors.UNBOUNDED.execute(() -> LogDatabase.getInstance(this).trimToSize());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new SignalUncaughtExceptionHandler(originalHandler));
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, ApplicationDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    ApplicationDependencies.getIncomingMessageObserver();
  }

  public void finalizeMessageRetrieval() {
    ApplicationDependencies.getIncomingMessageObserver().quit();
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

  private void initializeAppDependencies() {
    ApplicationDependencies.init(new ApplicationDependencyProvider(this));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this) || VersionTracker.getDaysSinceFirstInstalled(this) < 365) {
        Log.i(TAG, "First ever app launch!");
        AppInitialization.onFirstEverAppLaunch(this);
      }

      if (!IdentityKeyUtil.hasIdentityKey(this)) {
        Log.i(TAG, "Generating new identity keys...");
        IdentityKeyUtil.generateIdentityKeys(this);
      }

      Log.i(TAG, "Setting first install version to " + Util.getCanonicalVersionCode());
      TextSecurePreferences.setFirstInstallVersion(this, Util.getCanonicalVersionCode());
    }
  }

  private void initializeNetworkSettings() {
    NetworkManager nm = ApplicationDependencies.getNetworkManager();
    nm.setProxyChoice(TextSecurePreferences.getProxyType(this));
    nm.setProxySocksHost(TextSecurePreferences.getProxySocksHost(this));
    nm.setProxySocksPort(TextSecurePreferences.getProxySocksPort(this));
    nm.applyProxyConfig();
  }

  private void initializeGcmCheck() {
    if (!TextSecurePreferences.isPushRegistered(this)) {
      return;
    }

    PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(this);

    if (fcmStatus == PlayServicesUtil.PlayServicesStatus.DISABLED) {
      TextSecurePreferences.setFcmTokenLastSetTime(this, -1);
      if (!TextSecurePreferences.isFcmDisabled(this)) {
        Log.i(TAG, "Play Services are disabled. Disabling FCM.");
        TextSecurePreferences.setFcmDisabled(this, true);
        TextSecurePreferences.setFcmToken(this, null);
        ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      }
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS &&
               TextSecurePreferences.isFcmDisabled(this) &&
               TextSecurePreferences.getFcmTokenLastSetTime(this) < 0) {
      Log.i(TAG, "Play Services are newly-available. Updating to use FCM.");
      TextSecurePreferences.setFcmDisabled(this, false);
      ApplicationDependencies.getJobManager().startChain(new FcmRefreshJob())
                                             .then(new RefreshAttributesJob())
                                             .enqueue();
    } else {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    ApplicationDependencies.getExpiringMessageManager().checkSchedule();
  }

  private void finalizeExpiringMessageManager() {
    ApplicationDependencies.getExpiringMessageManager().quit();
  }

  private void initializeRevealableMessageManager() {
    ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary();
  }

  private void initializePendingRetryReceiptManager() {
    ApplicationDependencies.getPendingRetryReceiptManager().scheduleIfNecessary();
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    MessageProcessReceiver.startOrUpdateAlarm(this);

    if (TextSecurePreferences.isUpdateApkEnabled(this)) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      if (RtcDeviceLists.hardwareAECBlocked()) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!RtcDeviceLists.openSLESAllowed()) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      CallManager.initialize(this, new RingRtcLogger());
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @WorkerThread
  private void initializeCircumvention() {
    if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
      try {
        ProviderInstaller.installIfNeeded(ApplicationContext.this);
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob());
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
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
    int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
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
      onLock();
    }
  };

  private void registerKeyEventReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(KeyCachingService.CLEAR_KEY_EVENT);

    registerReceiver(keyEventReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
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
