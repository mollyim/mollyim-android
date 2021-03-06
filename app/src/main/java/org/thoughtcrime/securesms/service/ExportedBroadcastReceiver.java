package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public abstract class ExportedBroadcastReceiver extends BroadcastReceiver {

  @Override
  final public void onReceive(Context context, Intent intent) {
    if (KeyCachingService.isLocked()) {
      return;
    }
    onReceiveUnlock(context, intent);
  }

  protected void onReceiveUnlock(Context context, Intent intent) {}
}
