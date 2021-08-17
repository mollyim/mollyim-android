package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob;
import org.thoughtcrime.securesms.service.ExportedBroadcastReceiver;

public class LocaleChangedReceiver extends ExportedBroadcastReceiver {

  @Override
  public void onReceiveUnlock(Context context, Intent intent) {
    NotificationChannels.create(context);
    EmojiSearchIndexDownloadJob.scheduleImmediately();
  }
}
