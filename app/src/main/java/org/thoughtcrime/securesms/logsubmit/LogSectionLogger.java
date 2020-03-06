package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.LogManager;

import java.io.IOException;

public class LogSectionLogger implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGGER";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    try {
      return LogManager.getPersistentLogger().getLog();
    } catch (IOException e) {
      return "Failed to retrieve.";
    }
  }
}
