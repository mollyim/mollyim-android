package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

public class LogSectionLogcat implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGCAT";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    CharSequence logs = Log.getAndroidLogger().getLogcatDump();
    return logs != null ? logs : "Failed to get logcat dump";
  }
}
