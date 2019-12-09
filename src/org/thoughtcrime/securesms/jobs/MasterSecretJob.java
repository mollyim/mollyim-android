package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.MasterSecretConstraint;

abstract class MasterSecretJob extends Job {
  MasterSecretJob(@NonNull Parameters parameters) {
    super(addMasterSecretConstraint(parameters));
  }

  private static Parameters addMasterSecretConstraint(Parameters parameters) {
    return parameters.toBuilder().addConstraint(MasterSecretConstraint.KEY).build();
  }
}