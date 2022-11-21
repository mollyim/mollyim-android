package org.thoughtcrime.securesms.service;


import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.JavaTimeExtensionsKt;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  @Override
  protected boolean shouldScheduleExact() {
    return Build.VERSION.SDK_INT >= 31;
  }

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getNextBackupTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (SignalStore.settings().isBackupEnabled()) {
      LocalBackupJob.enqueue(shouldScheduleExact());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (SignalStore.settings().isBackupEnabled()) {
      new LocalBackupListener().onReceive(context, getScheduleIntent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    long nextTime = System.currentTimeMillis() + TextSecurePreferences.getBackupInternal(context);

    if (Build.VERSION.SDK_INT >= 31) {
      LocalDateTime nextInstant = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextTime),
                                                          TimeZone.getDefault().toZoneId());
      LocalDateTime next = nextInstant.withHour(2).withMinute(0).withSecond(0);
      if (nextInstant.getHour() >= 2) {
        next = next.plusDays(1);
      }

      nextTime = JavaTimeExtensionsKt.toMillis(next);
    }

    TextSecurePreferences.setNextBackupTime(context, nextTime);

    return nextTime;
  }
}
