package org.signal.core.util.logging;

import androidx.annotation.Nullable;

public class LogManager {

  private static final String TAG = Log.tag(LogManager.class);

  private static final NoopLogger    noopLogger    = new NoopLogger();
  private static final AndroidLogger androidLogger = new AndroidLogger();

  private static Log.Logger persistentLogger;

  private static Log.Logger compoundLogger = new CompoundLogger(androidLogger);

  private static Log.InternalCheck internalCheck = () -> false;

  public static AndroidLogger getAndroidLogger() {
    return androidLogger;
  }

  public static @Nullable Log.Logger getPersistentLogger() {
    return persistentLogger;
  }

  public static void setInternalCheck(Log.InternalCheck internalCheck) {
    LogManager.internalCheck = internalCheck;
  }

  public static void setPersistentLogger(Log.Logger persistentLogger) {
    LogManager.persistentLogger = persistentLogger;
  }

  public static void setLogging(boolean enabled) {
    if (enabled) {
      setLoggers(androidLogger, persistentLogger);
    } else {
      setLoggers(noopLogger);
    }
  }

  public static void wipeLogs() {
    getAndroidLogger().clear();
    Log.Logger local = getPersistentLogger();
    if (local != null) {
      local.clear();
    }
  }

  static Log.Logger getLogger() {
    return compoundLogger;
  }

  static Log.Logger getInternal() {
    if (internalCheck.isInternal()) {
      return compoundLogger;
    } else {
      return noopLogger;
    }
  }

  static void setLoggers(Log.Logger... loggers) {
    compoundLogger = new CompoundLogger(loggers);
  }
}
