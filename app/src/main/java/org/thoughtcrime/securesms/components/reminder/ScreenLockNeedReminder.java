package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

public class ScreenLockNeedReminder extends Reminder {

  private static final NavigableSet<Long> INTERVALS = new TreeSet<Long>() {{
    add(TimeUnit.DAYS.toMillis(1));
    add(TimeUnit.DAYS.toMillis(7));
    add(TimeUnit.DAYS.toMillis(30));
    add(TimeUnit.DAYS.toMillis(90));
  }};

  public ScreenLockNeedReminder(final @NonNull Context context) {
    super(context.getString(R.string.reminder_header_screen_lock_need_title),
          context.getString(R.string.reminder_header_screen_lock_need_text));

    setDismissListener(v -> {
      long lastReminderInterval = TextSecurePreferences.getScreenLockNeedReminderInterval(context);

      Long nextReminderInterval = INTERVALS.higher(lastReminderInterval);
      if (nextReminderInterval == null) nextReminderInterval = INTERVALS.last();

      long nextReminderTime = System.currentTimeMillis() + nextReminderInterval;

      TextSecurePreferences.setScreenLockNeedNextReminderTime(context, nextReminderTime);
      TextSecurePreferences.setScreenLockNeedReminderInterval(context, nextReminderInterval);
    });
  }

  public static boolean isEligible(final @NonNull Context context) {
    if (!MasterSecretUtil.isPassphraseInitialized(context) || !MasterSecretUtil.isKeyStoreInitialized(context)) {
      return false;
    }

    long nextReminderTime = TextSecurePreferences.getScreenLockNeedNextReminderTime(context);

    return System.currentTimeMillis() > nextReminderTime;
  }
}