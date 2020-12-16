package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.LogManager;

public class LogSectionLogcat implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGCAT";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    CharSequence logs = LogManager.getAndroidLogger().getLogs();
    return logs != null ? logs : "Unable to retrieve logs.";
  }
}
