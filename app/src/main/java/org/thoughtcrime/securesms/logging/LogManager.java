package org.thoughtcrime.securesms.logging;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

public class LogManager {

  private static final String TAG = Log.tag(LogManager.class);

  private static final AndroidLogger androidLogger = new AndroidLogger();

  private static PersistentLogger persistentLogger;

  private static Log.Logger[] loggers = { androidLogger };

  private final Context context;

  public LogManager(@NonNull Context context) {
    this.context = context;
  }

  public static Log.Logger getAndroidLogger() {
    return androidLogger;
  }

  public static Log.Logger getPersistentLogger() {
    Log.Logger logger = persistentLogger;
    if (logger == null) {
      logger = new EmptyLogger();
    }
    return logger;
  }

  @MainThread
  public void setLogging(boolean enabled) {
    if (enabled) {
      if (persistentLogger == null) {
        persistentLogger = new PersistentLogger(context);
      }
      setLoggers(androidLogger, persistentLogger);
    } else {
      setLoggers(new EmptyLogger());
    }
  }

  public void wipeLogs() {
    androidLogger.clear();
    if (persistentLogger != null) {
      persistentLogger.clear();
    }
  }

  static Log.Logger[] getLoggers() {
    return LogManager.loggers;
  }

  static void setLoggers(Log.Logger... loggers) {
    LogManager.loggers = loggers;
  }
}
