package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.AndroidLogger;

public class LogSectionLogcat implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGCAT";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    CharSequence logs = AndroidLogger.getLogcatDump();
    return logs != null ? logs : "Failed to get logcat dump";
  }

  @Override
  @NonNull public CharSequence getContentLocked(@NonNull Context context) {
    return getContent(context);
  }
}
