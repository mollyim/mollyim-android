package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.MessageFetchJob;

public class BootReceiver extends ExportedBroadcastReceiver {

  @Override
  public void onReceiveUnlock(Context context, Intent intent) {
    AppDependencies.getJobManager().add(new MessageFetchJob());
  }
}
