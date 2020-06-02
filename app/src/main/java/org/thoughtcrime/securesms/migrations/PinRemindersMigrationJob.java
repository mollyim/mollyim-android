package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.SecurePreferenceManager;

public class PinRemindersMigrationJob extends MigrationJob {

    private static final String TAG = Log.tag(PinRemindersMigrationJob.class);

    public static final String KEY = "PinRemindersMigrationJob";

    PinRemindersMigrationJob() {
        this(new Parameters.Builder().build());
    }

    private PinRemindersMigrationJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    public boolean isUiBlocking() {
        return false;
    }

    @Override
    public @NonNull String getFactoryKey() {
        return KEY;
    }

    @Override
    public void performMigration() {
        boolean value = SecurePreferenceManager.getSecurePreferences(context).getBoolean("pref_signal_enable_pinv2_reminders", true);
        SignalStore.pinValues().setPinRemindersEnabled(value);
    }

    @Override
    boolean shouldRetry(@NonNull Exception e) {
        return false;
    }

    public static class Factory implements Job.Factory<PinRemindersMigrationJob> {
        @Override
        public @NonNull
        PinRemindersMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new PinRemindersMigrationJob(parameters);
        }
    }
}
