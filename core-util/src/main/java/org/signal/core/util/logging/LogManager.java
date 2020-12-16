package org.signal.core.util.logging;

public class LogManager {

  private static final String TAG = Log.tag(LogManager.class);

  private static final AndroidLogger androidLogger = new AndroidLogger();

  private static PersistentLogger persistentLogger;

  private static Log.Logger[] loggers = { androidLogger };

  public static Log.Logger getAndroidLogger() {
    return androidLogger;
  }

  public static Log.Logger getPersistentLogger() {
    Log.Logger logger = persistentLogger;
    if (persistentLogger == null) {
      logger = new EmptyLogger();
    }
    return logger;
  }

  public static void setPersistentLogger(PersistentLogger logger) {
    persistentLogger = logger;
  }

  public static void setLogging(boolean enabled) {
    if (enabled) {
      setLoggers(androidLogger, persistentLogger);
    } else {
      setLoggers(new EmptyLogger());
    }
  }

  public static void wipeLogs() {
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
