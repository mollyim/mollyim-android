package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.SecurePreferenceManager;

import java.util.concurrent.TimeUnit;

public class PinReminderMigrationJob extends MigrationJob {

  public static final String KEY = "PinReminderMigrationJob";

  PinReminderMigrationJob() {
    this(new Job.Parameters.Builder().build());
  }

  private PinReminderMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  void performMigration() {
    boolean value = SecurePreferenceManager.getSecurePreferences(context).getBoolean("pref_signal_enable_pinv2_reminders", true);
    SignalStore.pinValues().setPinRemindersEnabled(value);
    SignalStore.pinValues().setNextReminderIntervalToAtMost(TimeUnit.DAYS.toMillis(3));
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<PinReminderMigrationJob> {

    @Override
    public @NonNull PinReminderMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PinReminderMigrationJob(parameters);
    }
  }
}
