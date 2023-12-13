package org.thoughtcrime.securesms.jobmanager.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.JobMigration;

public class RecipientIdFollowUpJobMigration extends JobMigration {

  public RecipientIdFollowUpJobMigration() {
    this(3);
  }

  RecipientIdFollowUpJobMigration(int endVersion) {
    super(endVersion);
  }

  @Override
  public @NonNull JobData migrate(@NonNull JobData jobData) {
    return jobData;
  }
}
