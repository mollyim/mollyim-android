package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.LogManager;
import org.signal.core.util.logging.PersistentLogger;

public class LogSectionLogger implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "LOGGER";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    PersistentLogger persistentLogger = LogManager.getPersistentLogger();
    if (persistentLogger == null) {
      return "Logs not available";
    }
    CharSequence logs = persistentLogger.getLogs();
    return logs != null ? logs : "Unable to retrieve logs.";
  }
}
