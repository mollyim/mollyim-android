package org.thoughtcrime.securesms.logging;

import androidx.annotation.MainThread;

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
    for (Logger logger : LogManager.getLoggers()) {
      logger.v(tag, message, t);
    }
  }

  public static void d(String tag, String message, Throwable t) {
    for (Logger logger : LogManager.getLoggers()) {
      logger.d(tag, message, t);
    }
  }

  public static void i(String tag, String message, Throwable t) {
    for (Logger logger : LogManager.getLoggers()) {
      logger.i(tag, message, t);
    }
  }

  public static void w(String tag, String message, Throwable t) {
    for (Logger logger : LogManager.getLoggers()) {
      logger.w(tag, message, t);
    }
  }

  public static void e(String tag, String message, Throwable t) {
    for (Logger logger : LogManager.getLoggers()) {
      logger.e(tag, message, t);
    }
  }

  public static void wtf(String tag, String message, Throwable t) {
    for (Logger logger : LogManager.getLoggers()) {
      logger.wtf(tag, message, t);
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

  public static abstract class Logger {
    public abstract void v(String tag, String message, Throwable t);
    public abstract void d(String tag, String message, Throwable t);
    public abstract void i(String tag, String message, Throwable t);
    public abstract void w(String tag, String message, Throwable t);
    public abstract void e(String tag, String message, Throwable t);
    public abstract void wtf(String tag, String message, Throwable t);
    public abstract void blockUntilAllWritesFinished();
    public abstract void clear();
    public abstract String getLog() throws IOException;
  }
}
