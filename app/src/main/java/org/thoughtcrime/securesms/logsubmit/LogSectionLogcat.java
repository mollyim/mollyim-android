package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.LogManager;

import java.io.IOException;

public class LogSectionLogcat implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGCAT";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    try {
      return LogManager.getAndroidLogger().getLog();
    } catch (IOException ioe) {
      return "Failed to retrieve.";
    }
  }
}
