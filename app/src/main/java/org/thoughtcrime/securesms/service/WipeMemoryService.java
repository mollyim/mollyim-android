package org.thoughtcrime.securesms.service;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.gcm.FcmFetchManager;
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.WakeLockUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.MIN_PRIORITY;

public class WipeMemoryService extends IntentService {

  private static final String TAG = Log.tag(WipeMemoryService.class);

  public static final int NOTIFICATION_ID = 4343;

  private static final float LOW_MEMORY_THRESHOLD_ADJ = 2.00f;

  private static final long WAKE_LOCK_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

  static {
    System.loadLibrary("native-utils");
  }

  private static native long allocPages(int count);
  private static native void freePages(long p);
  private static native void wipePage(long p, int index);
  private static native int  getPageSize();

  private static boolean restart;

  private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

  private NotificationManagerCompat  notificationManager;
  private NotificationCompat.Builder notification;

  private ActivityManager activityManager;

  private volatile boolean lowMemory;

  private PowerManager.WakeLock busyWakeLock;

  private long lastProgressNotification;

  public static void run(Context context, boolean restartApp) {
    restart = restartApp;
    Intent intent = new Intent(context, WipeMemoryService.class);
    ForegroundServiceUtil.startWhenCapableOrThrow(context, intent);
  }

  public WipeMemoryService() {
    super("WipeMemoryService");
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (restart) {
      stopService(new Intent(this, KeyCachingService.class));
      killProcess();
    }
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    long bytes = doWipe();
    Log.i(TAG, "Free memory wiped: " + bytes + " bytes");
  }

  private long doWipe() {
    int pageSize = getPageSize();
    int chunkPageLimit = 4096 * 1024 / pageSize;

    int maxChunks = (int) (getTotalMemory() / pageSize / chunkPageLimit);
    List<Long> chunks = new ArrayList<>(maxChunks);

    long overwritten = 0;

    int savePriority = Thread.currentThread().getPriority();

    try {
      Thread.currentThread().setPriority(MIN_PRIORITY);

      long memFree = getFreeMemory();

      long maxProgress = memFree;
      long progress = 0;

      while (memFree >= pageSize) {
        if (lowMemory) {
          return overwritten;
        }

        int pageCount = Math.min(chunkPageLimit, (int) (memFree / pageSize));
        long ptr = allocPages(pageCount);

        // If returned a NULL pointer, the memory is exhausted.
        if (ptr == 0) {
          return overwritten;
        }

        chunks.add(ptr);

        for (int i = 0; (i < pageCount); i++) {
          wipePage(ptr, i);
          overwritten += pageSize;
          if (lowMemory) {
            return overwritten;
          }
        }

        memFree = getFreeMemory();

        if (memFree < maxProgress) {
          progress = Math.max(progress, maxProgress - memFree);
          showProgress(maxProgress, progress);
        }
      }
    } catch (Error ignored) {
      // Defensively catch all runtime errors including OOM
    } finally {
      for (Long ptr : chunks) {
        freePages(ptr);
      }

      Thread.currentThread().setPriority(savePriority);
    }

    return overwritten;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();
    showForegroundNotification();
    busyWakeLock = WakeLockUtil.acquire(this, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TIMEOUT, TAG);
    notificationManager = NotificationManagerCompat.from(this);
    activityManager = ServiceUtil.getActivityManager(this);
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
    super.onDestroy();
    hideForegroundNotification();
    WakeLockUtil.release(busyWakeLock, TAG);
  }

  @Override
  public void onTrimMemory(int level) {
    if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
      notifyLowMemory();
    }
    Log.i(TAG, "onTrimMemory(" + level +")");
    super.onTrimMemory(level);
  }

  @Override
  public void onLowMemory() {
    notifyLowMemory();
    Log.i(TAG, "onLowMemory()");
    super.onLowMemory();
  }

  @Override
  public void onTimeout(int startId) {
    notifyLowMemory();
    Log.i(TAG, "onTimeout()");
    super.onTimeout(startId);
  }

  private void notifyLowMemory() {
    lowMemory = true;
  }

  private long getFreeMemory() {
    activityManager.getMemoryInfo(memoryInfo);
    long freeMem = (long) (memoryInfo.availMem - (memoryInfo.threshold * LOW_MEMORY_THRESHOLD_ADJ));
    return (freeMem > 0) ? freeMem : 0;
  }

  private long getTotalMemory() {
    activityManager.getMemoryInfo(memoryInfo);
    return memoryInfo.totalMem;
  }

  private void showForegroundNotification() {
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntentFlags.immutable());
    notification = new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS)
        .setOngoing(true)
        .setContentTitle(getString(R.string.WipeMemoryService_secure_wipe_in_progress))
        .setContentText(getString(R.string.WipeMemoryService_clearing_unencrypted_data_from_ram))
        .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.WipeMemoryService_clearing_unencrypted_data_from_ram)))
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(getResources().getColor(R.color.core_ultramarine))
        .setContentIntent(pendingIntent)
        .setShowWhen(false)
        .setProgress(0, 0, true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS);
    startForeground(NOTIFICATION_ID, notification.build());
  }

  private void hideForegroundNotification() {
    notificationManager.cancel(NOTIFICATION_ID);
  }

  private void showProgress(long totalBytes, long progressBytes) {
    long now = System.currentTimeMillis();
    if (now - lastProgressNotification < 250L) {
      // Updating too frequently
      return;
    }
    lastProgressNotification = now;
    notification.setProgress((int) (totalBytes / 1024), (int) (progressBytes / 1024), false);
    notificationManager.notify(NOTIFICATION_ID, notification.build());
  }

  private static void killProcess() {
    System.exit(0);
  }
}
