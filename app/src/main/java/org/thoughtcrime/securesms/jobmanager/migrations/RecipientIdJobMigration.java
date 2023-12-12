package org.thoughtcrime.securesms.jobmanager.migrations;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.JobMigration;

public class RecipientIdJobMigration extends JobMigration {

  private final Application application;

  public RecipientIdJobMigration(@NonNull Application application) {
    super(2);
    this.application = application;
  }

  @Override
  public @NonNull JobData migrate(@NonNull JobData jobData) {
    return jobData;
  }
}
