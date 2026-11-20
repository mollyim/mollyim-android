package org.thoughtcrime.securesms.service;


import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.JavaTimeExtensionsKt;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final int BACKUP_JITTER_WINDOW_SECONDS = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10));

  @Override
  protected boolean shouldScheduleExact() {
    return true;
  }

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return SignalStore.settings().getLocalBackupNextTime();
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (SignalStore.settings().isBackupEnabled()) {
      LocalBackupJob.enqueue(false);
    }

    if (SignalStore.backup().getNewLocalBackupsEnabled()) {
      LocalBackupJob.enqueueArchive(SignalStore.settings().isBackupEnabled());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (SignalStore.settings().isBackupEnabled() || SignalStore.backup().getNewLocalBackupsEnabled()) {
      new LocalBackupListener().onReceive(context, getScheduleIntent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    long nowPlusInterval = System.currentTimeMillis() + SignalStore.molly().getBackupInterval();

    LocalDateTime nextInstant = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowPlusInterval),
                                                        TimeZone.getDefault().toZoneId());
    int           hour   = SignalStore.settings().getBackupHour();
    int           minute = SignalStore.settings().getBackupMinute();
    LocalDateTime next   = nextInstant.withHour(hour).withMinute(minute).withSecond(0);

    int jitter = (new Random().nextInt(BACKUP_JITTER_WINDOW_SECONDS)) - (BACKUP_JITTER_WINDOW_SECONDS / 2);

    next = next.plusSeconds(jitter);

    if (nextInstant.isAfter(next)) {
      next = next.plusDays(1);
    }

    long nextTime = JavaTimeExtensionsKt.toMillis(next);

    SignalStore.settings().setLocalBackupNextTime(nextTime);

    return nextTime;
  }
}
