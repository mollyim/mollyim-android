package org.signal.core.util.logging;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@SuppressLint("LogNotSignal")
public final class AndroidLogger extends Log.Logger {

  @Override
  public void v(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.v(tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.d(tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.i(tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.w(tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.e(tag, message, t);
  }

  @Override
  public void flush() {
  }

  @Override
  public void clear() {
    try {
      Runtime.getRuntime().exec("logcat -c");
    } catch (IOException ignored) {}
  }

  @WorkerThread
  public @Nullable CharSequence getLogcatDump() {
    try {
      final Process process = Runtime.getRuntime().exec("logcat -d");
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder log = new StringBuilder();
      final String separator = System.getProperty("line.separator");

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log;
    } catch (IOException ioe) {
      return null;
    }
  }
}
