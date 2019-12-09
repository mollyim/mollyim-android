package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.app.job.JobInfo;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Constraint;
import org.thoughtcrime.securesms.service.KeyCachingService;

public class MasterSecretConstraint implements Constraint {

  public static final String KEY = "MasterSecretConstraint";

  @NonNull
  @Override
  public String getFactoryKey() {
    return KEY;
  }

  @Override
  public boolean isMet() {
    return !KeyCachingService.isLocked();
  }

  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {}

  public static final class Factory implements Constraint.Factory<MasterSecretConstraint> {

    public Factory(@NonNull Application application) {}

    @Override
    public MasterSecretConstraint create() {
      return new MasterSecretConstraint();
    }
  }
}
