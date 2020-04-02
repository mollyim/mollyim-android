package org.thoughtcrime.securesms.service;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.MIN_PRIORITY;

public class WipeMemoryService extends IntentService {

  private static final String TAG = Log.tag(WipeMemoryService.class);

  private static final int NOTIFICATION_ID = 4343;

  private static final float LOW_MEMORY_THRESHOLD_ADJ = 2.00f;

  static {
    System.loadLibrary("native-utils");
  }

  private static native long allocPages(int count);
  private static native void freePages(long p);
  private static native void wipePage(long p, int index);
  private static native int  getPageSize();

  private static boolean restart;

  private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

  private ActivityManager activityManager;

  private volatile boolean lowMemory;

  public static void run(Context context, boolean restartApp) {
    restart = restartApp;
    startForegroundService(context);
  }

  private static void startForegroundService(Context context) {
    Intent intent = new Intent(context, WipeMemoryService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  public WipeMemoryService() {
    super("WipeMemoryService");
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (restart) {
      killProcess();
    }
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    Thread.currentThread().setPriority(MIN_PRIORITY);
    long bytes = doWipe();
    Log.i(TAG, "Free memory wiped: " + bytes + " bytes");
  }

  private long doWipe() {
    int pageSize  = getPageSize();
    int chunkSize = 4096 * 1024 / pageSize;
    int maxChunks = (int) (getTotalMemory() / pageSize / chunkSize);

    List<Long> chunks = new ArrayList<>(maxChunks);

    long total = 0;

    try {
      while (!lowMemory) {
        int freePages = (int) (getFreeMemory() / pageSize);

        if (freePages == 0) break;

        int pageCount = chunkSize;
        if (pageCount > freePages) {
          pageCount = freePages;
        }

        long ptr = allocPages(pageCount);

        // If returned a NULL pointer, the memory is exhausted.
        if (ptr == 0) break;

        chunks.add(ptr);

        for (int i = 0; i < pageCount; i++) {
          if (lowMemory) break;

          wipePage(ptr, i);
          total += pageSize;
        }
      }
    } catch (Error ignored) {
      // Defensively catch all runtime errors including OOM
    } finally {
      for (Long ptr : chunks) {
        freePages(ptr);
      }
    }

    return total;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();
    activityManager = ServiceUtil.getActivityManager(this);
    if (!restart) {
      showForegroundNotification();
    }
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
    super.onDestroy();
    hideForegroundNotification();
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
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
    Notification notification =
            new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS)
                    .setContentTitle(this.getString(R.string.WipeMemoryService_secure_wipe_in_progress))
                    .setContentText(this.getString(R.string.WipeMemoryService_molly_is_clearing_secrets))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(this.getString(R.string.WipeMemoryService_molly_is_clearing_secrets)))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
    startForeground(NOTIFICATION_ID, notification);
  }

  private void hideForegroundNotification() {
    stopForeground(true);
  }

  private static void killProcess() {
    System.exit(0);
  }
}
