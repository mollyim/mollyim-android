package org.signal.core.util.logging;

import android.annotation.SuppressLint;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.signal.core.util.BuildConfig;

@SuppressLint("LogNotSignal")
public final class Log {

  /**
   * @param internalCheck A checker that will indicate if this is an internal user
   * @param loggers A list of loggers that will be given every log statement.
   */
  @MainThread
  public static void initialize(@NonNull InternalCheck internalCheck, Logger... loggers) {
    LogManager.setInternalCheck(internalCheck);
    LogManager.setLoggers(loggers);
  }

  public static void initialize(Logger... loggers) {
    LogManager.setLoggers(loggers);
  }

  private static String redact(final String message) {
    if (message == null) {
      return null;
    }
    if (BuildConfig.DEBUG || message.isEmpty()) {
      return message;
    }
    return Scrubber.scrub(message).toString();
  }

  public static void v(String tag, String message) {
    v(tag, message, null);
  }

  public static void d(String tag, String message) {
    d(tag, message, null);
  }

  public static void i(String tag, String message) {
    i(tag, message, null);
  }

  public static void w(String tag, String message) {
    w(tag, message, null);
  }

  public static void e(String tag, String message) {
    e(tag, message, null);
  }

  public static void v(String tag, Throwable t) {
    v(tag, null, t);
  }

  public static void d(String tag, Throwable t) {
    d(tag, null, t);
  }

  public static void i(String tag, Throwable t) {
    i(tag, null, t);
  }

  public static void w(String tag, Throwable t) {
    w(tag, null, t);
  }

  public static void e(String tag, Throwable t) {
    e(tag, null, t);
  }

  public static void v(String tag, String message, Throwable t) {
    v(tag, message, t, false);
  }

  public static void d(String tag, String message, Throwable t) {
    d(tag, message, t, false);
  }

  public static void i(String tag, String message, Throwable t) {
    i(tag, message, t, false);
  }

  public static void w(String tag, String message, Throwable t) {
    w(tag, message, t, false);
  }

  public static void e(String tag, String message, Throwable t) {
    e(tag, message, t, false);
  }

  public static void v(String tag, String message, boolean keepLonger) {
    v(tag, message, null, keepLonger);
  }

  public static void d(String tag, String message, boolean keepLonger) {
    d(tag, message, null, keepLonger);
  }

  public static void i(String tag, String message, boolean keepLonger) {
    i(tag, message, null, keepLonger);
  }

  public static void w(String tag, String message, boolean keepLonger) {
    w(tag, message, null, keepLonger);
  }

  public static void e(String tag, String message, boolean keepLonger) {
    e(tag, message, null, keepLonger);
  }

  public static void v(String tag, String message, Throwable t, boolean keepLonger) {
    LogManager.getLogger().v(tag, redact(message), t, keepLonger);
  }

  public static void d(String tag, String message, Throwable t, boolean keepLonger) {
    LogManager.getLogger().d(tag, redact(message), t, keepLonger);
  }

  public static void i(String tag, String message, Throwable t, boolean keepLonger) {
    LogManager.getLogger().i(tag, redact(message), t, keepLonger);
  }

  public static void w(String tag, String message, Throwable t, boolean keepLonger) {
    LogManager.getLogger().w(tag, redact(message), t, keepLonger);
  }

  public static void e(String tag, String message, Throwable t, boolean keepLonger) {
    LogManager.getLogger().e(tag, redact(message), t, keepLonger);
  }

  public static String tag(Class<?> clazz) {
    String simpleName = clazz.getSimpleName();
    if (simpleName.length() > 23) {
      return simpleName.substring(0, 23);
    }
    return simpleName;
  }

  /**
   * Important: This is not something that can be used to log PII. Instead, it's intended use is for
   * logs that might be too verbose or otherwise unnecessary for public users.
   *
   * @return The normal logger if this is an internal user, or a no-op logger if it isn't.
   */
  public static Logger internal() {
    return LogManager.getInternal();
  }

  public static void blockUntilAllWritesFinished() {
    LogManager.getLogger().flush();
  }

  public static abstract class Logger {

    public abstract void v(String tag, String message, Throwable t, boolean keepLonger);
    public abstract void d(String tag, String message, Throwable t, boolean keepLonger);
    public abstract void i(String tag, String message, Throwable t, boolean keepLonger);
    public abstract void w(String tag, String message, Throwable t, boolean keepLonger);
    public abstract void e(String tag, String message, Throwable t, boolean keepLonger);
    public abstract void flush();
    public abstract void clear();

    public void v(String tag, String message, boolean keepLonger) {
      v(tag, message, null, keepLonger);
    }

    public void d(String tag, String message, boolean keepLonger) {
      d(tag, message, null, keepLonger);
    }

    public void i(String tag, String message, boolean keepLonger) {
      i(tag, message, null, keepLonger);
    }

    public void w(String tag, String message, boolean keepLonger) {
      w(tag, message, null, keepLonger);
    }

    public void e(String tag, String message, boolean keepLonger) {
      e(tag, message, null, keepLonger);
    }

    public void v(String tag, String message, Throwable t) {
      v(tag, message, t, false);
    }

    public void d(String tag, String message, Throwable t) {
      d(tag, message, t, false);
    }

    public void i(String tag, String message, Throwable t) {
      i(tag, message, t, false);
    }

    public void w(String tag, String message, Throwable t) {
      w(tag, message, t, false);
    }

    public void e(String tag, String message, Throwable t) {
      e(tag, message, t, false);
    }

    public void v(String tag, String message) {
      v(tag, message, null);
    }

    public void d(String tag, String message) {
      d(tag, message, null);
    }

    public void i(String tag, String message) {
      i(tag, message, null);
    }

    public void w(String tag, String message) {
      w(tag, message, null);
    }

    public void e(String tag, String message) {
      e(tag, message, null);
    }
  }

  public interface InternalCheck {
    boolean isInternal();
  }
}
