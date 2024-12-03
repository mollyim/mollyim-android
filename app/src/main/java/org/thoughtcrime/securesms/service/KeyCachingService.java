/*
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.DummyActivity;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

/**
 * Small service that stays running to keep a key cached in memory.
 *
 * @author Moxie Marlinspike
 */

public class KeyCachingService extends Service {

  private static final String TAG = Log.tag(KeyCachingService.class);

  public static final int SERVICE_RUNNING_ID = 4141;

  public  static final String KEY_PERMISSION           = BuildConfig.APPLICATION_ID + ".ACCESS_SECRETS";
  public  static final String CLEAR_KEY_EVENT          = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = BuildConfig.APPLICATION_ID + ".service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY";
  public  static final String LOCALE_CHANGE_EVENT      = BuildConfig.APPLICATION_ID + ".service.action.LOCALE_CHANGE_EVENT";

  public static final String EXTRA_KEY_EXPIRED = "extra.key_expired";

  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private final IBinder binder  = new KeySetBinder();

  private boolean pendingAlarm;

  private static MasterSecret masterSecret;

  private static volatile boolean locking;

  public KeyCachingService() {}

  public static synchronized boolean isLocked() {
    return masterSecret == null || locking;
  }

  public static synchronized MasterSecret getMasterSecret() {
    if (masterSecret == null) {
      throw new IllegalStateException();
    }
    return masterSecret.clone();
  }

  public static synchronized void setMasterSecret(final MasterSecret newMasterSecret) {
    masterSecret = newMasterSecret;
  }

  public static synchronized void clearMasterSecret() {
    if (masterSecret != null) {
      masterSecret.close();
      masterSecret = null;
    }
    locking = false;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || (intent.getAction() != null && isLocked())) {
      return START_NOT_STICKY;
    }
    Log.d(TAG, "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION -> handleClearKey(false);
        case PASSPHRASE_EXPIRED_EVENT -> handleClearKey(true);
        case LOCALE_CHANGE_EVENT -> handleLocaleChanged();
      }
    } else {
      handleCacheKey();
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");
    registerScreenReceiver();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w(TAG, "KCS Is Being Destroyed!");
    unregisterScreenReceiver();
    if (locking) {
      clearMasterSecret();
    }
  }

  /**
   * Workaround for Android bug:
   * https://code.google.com/p/android/issues/detail?id=53313
   */
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Intent intent = new Intent(this, DummyActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private void handleCacheKey() {
    Log.i(TAG, "handleCacheKey");

    foregroundService();

    if (ServiceUtil.getKeyguardManager(this).isKeyguardLocked()) {
      startTimeoutIfAppropriate();
    }

    SignalExecutors.BOUNDED.execute(() -> {
      if (!ApplicationMigrations.isUpdate(KeyCachingService.this)) {
        AppDependencies.getMessageNotifier().updateNotification(KeyCachingService.this);
      }
    });
  }

  private void handleClearKey(boolean keyExpired) {
    Log.d(TAG, "handleClearKey() keyExpired: " + keyExpired);

    cancelTimeout();

    if ((ApplicationMigrations.isUpdate(this) && ApplicationMigrations.isUiBlockingMigrationRunning()) ||
        (EventBus.getDefault().getStickyEvent(TransferStatus.class) != null)) {
      Log.w(TAG, "Cannot lock during app migration or device transfer.");
      return;
    }

    KeyCachingService.locking = true;

    Log.i(TAG, "Broadcasting " + CLEAR_KEY_EVENT);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.putExtra(EXTRA_KEY_EXPIRED, keyExpired);
    intent.setPackage(getPackageName());
    sendBroadcast(intent, KEY_PERMISSION);
  }

  private void handleLocaleChanged() {
    dynamicLanguage.updateServiceLocale(this);
    foregroundService();
  }

  private void startTimeoutIfAppropriate() {
    if (!KeyCachingService.isLocked()
        && TextSecurePreferences.isPassphraseLockEnabled(this)
        && TextSecurePreferences.getPassphraseLockTrigger(this).isTimeoutEnabled()) {
      long lockTimeoutSeconds = TextSecurePreferences.getPassphraseLockTimeout(this);
      scheduleTimeout(lockTimeoutSeconds);
    } else {
      cancelTimeout();
    }
  }

  private synchronized void scheduleTimeout(long timeoutSeconds) {
    if (pendingAlarm) {
      return;
    }

    Log.i(TAG, "Starting timeout: " + timeoutSeconds + " s.");

    long at = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

    AlarmManager alarmManager = ServiceUtil.getAlarmManager(this);
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, buildExpirationIntent());

    pendingAlarm = true;
  }

  private synchronized void cancelTimeout() {
    pendingAlarm = false;

    AlarmManager alarmManager = ServiceUtil.getAlarmManager(this);
    alarmManager.cancel(buildExpirationIntent());

    Log.i(TAG, "Timeout canceled");
  }

  private void foregroundService() {
    if (!TextSecurePreferences.isPassphraseLockEnabled(this)) {
      stopForeground(true);
      return;
    }

    Log.i(TAG, "foregrounding KCS");
    NotificationChannels.getInstance();
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setSmallIcon(R.drawable.ic_notification_unlocked);
    builder.setColor(ContextCompat.getColor(this, R.color.signal_light_colorSecondary));
    builder.setWhen(0);
    builder.setPriority(NotificationCompat.PRIORITY_LOW);
    builder.setCategory(NotificationCompat.CATEGORY_STATUS);
    builder.setOngoing(true);

    builder.addAction(R.drawable.symbol_lock_24, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(CLEAR_KEY_ACTION);
    return PendingIntent.getService(getApplicationContext(), 0, intent, getPendingIntentFlags());
  }

  private PendingIntent buildLaunchIntent() {
    // TODO [greyson] Navigation
    return PendingIntent.getActivity(getApplicationContext(), 0, MainActivity.clearTop(this), getPendingIntentFlags());
  }

  private PendingIntent buildExpirationIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    return PendingIntent.getService(getApplicationContext(), 0, intent, getPendingIntentFlags());
  }

  private static int getPendingIntentFlags() {
    return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeySetBinder extends Binder {
    public KeyCachingService getService() {
      return KeyCachingService.this;
    }
  }

  private void registerScreenReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_USER_PRESENT);

    registerReceiver(screenReceiver, filter);
  }

  private void unregisterScreenReceiver() {
    unregisterReceiver(screenReceiver);
  }

  private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      Log.d(TAG, "onReceive, " + action);
           if (Intent.ACTION_SCREEN_OFF  .equals(action)) startTimeoutIfAppropriate();
      else if (Intent.ACTION_USER_PRESENT.equals(action)) cancelTimeout();
    }
  };
}
