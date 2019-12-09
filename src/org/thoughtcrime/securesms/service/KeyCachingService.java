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

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.DummyActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
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

  private static final String TAG = KeyCachingService.class.getSimpleName();

  public static final int SERVICE_RUNNING_ID = 4141;
  public  static final String KEY_PERMISSION           = BuildConfig.APPLICATION_ID + ".ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = BuildConfig.APPLICATION_ID + ".service.action.NEW_KEY_EVENT";
  public  static final String CLEAR_KEY_EVENT          = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = BuildConfig.APPLICATION_ID + ".service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY";
  public  static final String LOCALE_CHANGE_EVENT      = BuildConfig.APPLICATION_ID + ".service.action.LOCALE_CHANGE_EVENT";

  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private final IBinder binder  = new KeySetBinder();

  private static MasterSecret masterSecret;

  private static volatile boolean locking;

  public KeyCachingService() {}

  public static boolean isLocked(Context context) {
    return isLocked();
  }

  public static synchronized boolean isLocked() {
    return masterSecret == null || locking;
  }

  public static synchronized MasterSecret getMasterSecret() {
    while (masterSecret == null) {
      try {
        KeyCachingService.class.wait();
      } catch (InterruptedException ie) {
        Log.w(TAG, ie);
      }
    }

    return masterSecret.clone();
  }

  public static synchronized void clearMasterSecret() {
    if (masterSecret != null) {
      masterSecret.close();
      masterSecret = null;
    }
    locking = false;
  }

  public static void onAppForegrounded(@NonNull Context context) {
    ServiceUtil.getAlarmManager(context).cancel(buildExpirationPendingIntent(context));
  }

  public static void onAppBackgrounded(@NonNull Context context) {
    startTimeoutIfAppropriate(context);
  }

  @SuppressLint("StaticFieldLeak")
  public void setMasterSecret(final MasterSecret masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret = masterSecret;
      KeyCachingService.class.notifyAll();

      foregroundService();
      broadcastNewSecret();
      startTimeoutIfAppropriate(this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          if (!ApplicationMigrations.isUpdate(KeyCachingService.this)) {
            MessageNotifier.updateNotification(KeyCachingService.this);
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    Log.d(TAG, "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey();        break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey();        break;
        case LOCALE_CHANGE_EVENT:      handleLocaleChanged();   break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w(TAG, "KCS Is Being Destroyed!");
    if (locking) clearMasterSecret();
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

  @SuppressLint("StaticFieldLeak")
  private void handleClearKey() {
    Log.i(TAG, "handleClearKey()");

    if (ApplicationMigrations.isUpdate(this)) {
      Log.w(TAG, "Cannot clear key during update.");
      return;
    }

    KeyCachingService.locking = true;
    stopSelf();

    sendPackageBroadcast(CLEAR_KEY_EVENT);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.clearNotifications(KeyCachingService.this, true);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleLocaleChanged() {
    dynamicLanguage.updateServiceLocale(this);
    foregroundService();
  }

  private static void startTimeoutIfAppropriate(@NonNull Context context) {
    if (!ApplicationContext.getInstance(context).isAppVisible()
        && !KeyCachingService.isLocked()
        && TextSecurePreferences.isPassphraseLockEnabled(context)
        && TextSecurePreferences.getPassphraseLockTrigger(context).isTimeoutEnabled()) {
      long lockTimeoutSeconds = TextSecurePreferences.getPassphraseLockTimeout(context);
      if (lockTimeoutSeconds > 0) {
        scheduleTimeout(context, lockTimeoutSeconds, buildExpirationPendingIntent(context));
      }
    }
  }

  private static void scheduleTimeout(@NonNull Context context, long timeoutSeconds, PendingIntent operation) {
    Log.i(TAG, "Starting timeout: " + timeoutSeconds + " s.");

    long at = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

    AlarmManager alarmManager = ServiceUtil.getAlarmManager(context);
    alarmManager.cancel(operation);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation);
    } else {
      alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation);
    }
  }

  private void foregroundService() {
    if (!TextSecurePreferences.isPassphraseLockEnabled(this)) {
      stopForeground(true);
      return;
    }

    Log.i(TAG, "foregrounding KCS");
    NotificationChannels.create(this);
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_MIN);

    builder.addAction(R.drawable.ic_menu_lock_dark, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void broadcastNewSecret() {
    sendPackageBroadcast(NEW_KEY_EVENT);
  }

  private void sendPackageBroadcast(String action) {
    Log.i(TAG, "Broadcasting " + action);

    Intent intent = new Intent(action);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    return PendingIntent.getService(getApplicationContext(), 0, intent, 0);
  }

  private PendingIntent buildLaunchIntent() {
    // TODO [greyson] Navigation
    Intent intent              = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
  }

  private static PendingIntent buildExpirationPendingIntent(@NonNull Context context) {
    Intent expirationIntent = new Intent(PASSPHRASE_EXPIRED_EVENT, null, context, KeyCachingService.class);
    return PendingIntent.getService(context, 0, expirationIntent, 0);
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
}
