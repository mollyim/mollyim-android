package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.MessageFetchJob;

public class BootReceiver extends ExportedBroadcastReceiver {

  private static final String TAG = Log.tag(BootReceiver.class);

  @Override
  public void onReceiveUnlock(Context context, Intent intent) {
    Log.i(TAG, "Restarting after: " + intent.getAction());
    AppDependencies.getJobManager().add(new MessageFetchJob());
  }
}
