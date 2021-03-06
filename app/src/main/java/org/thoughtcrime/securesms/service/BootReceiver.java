package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;

public class BootReceiver extends ExportedBroadcastReceiver {

  @Override
  public void onReceiveUnlock(Context context, Intent intent) {
    ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob());
  }
}
