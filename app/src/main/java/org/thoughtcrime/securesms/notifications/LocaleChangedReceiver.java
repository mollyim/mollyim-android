package org.thoughtcrime.securesms.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DateUtils;

public class LocaleChangedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    DateUtils.updateFormat();
    if (!KeyCachingService.isLocked()) {
      NotificationChannels.getInstance().onLocaleChanged();
      EmojiSearchIndexDownloadJob.scheduleImmediately();
    }
  }
}
