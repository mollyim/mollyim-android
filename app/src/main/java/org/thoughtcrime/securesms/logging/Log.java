package org.thoughtcrime.securesms.logging;

import androidx.annotation.MainThread;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.logsubmit.util.Scrubber;

import java.io.IOException;

public class Log {

  @MainThread
  public static void initialize(Logger... loggers) {
    LogManager.setLoggers(loggers);
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

  public static void wtf(String tag, String message) {
    wtf(tag, message, null);
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

  public static void wtf(String tag, Throwable t) {
    wtf(tag, null, t);
  }

  public static void v(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.v(tag, censored, t);
    }
  }

  public static void d(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.d(tag, censored, t);
    }
  }

  public static void i(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.i(tag, censored, t);
    }
  }

  public static void w(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.w(tag, censored, t);
    }
  }

  public static void e(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.e(tag, censored, t);
    }
  }

  public static void wtf(String tag, String message, Throwable t) {
    String censored = redact(message);
    for (Logger logger : LogManager.getLoggers()) {
      logger.wtf(tag, censored, t);
    }
  }

  public static String tag(Class<?> clazz) {
    String simpleName = clazz.getSimpleName();
    if (simpleName.length() > 23) {
      return simpleName.substring(0, 23);
    }
    return simpleName;
  }

  public static void blockUntilAllWritesFinished() {
    for (Logger logger : LogManager.getLoggers()) {
      logger.blockUntilAllWritesFinished();
    }
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

  public static abstract class Logger {
    public abstract void v(String tag, String message, Throwable t);
    public abstract void d(String tag, String message, Throwable t);
    public abstract void i(String tag, String message, Throwable t);
    public abstract void w(String tag, String message, Throwable t);
    public abstract void e(String tag, String message, Throwable t);
    public abstract void wtf(String tag, String message, Throwable t);
    public abstract void blockUntilAllWritesFinished();
    public abstract void clear();
    public abstract CharSequence getLog() throws IOException;
  }
}
