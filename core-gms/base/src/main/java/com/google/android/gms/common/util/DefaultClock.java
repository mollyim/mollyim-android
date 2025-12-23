package com.google.android.gms.common.util;

import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class DefaultClock implements Clock {

  private DefaultClock() {}

  private static final class InstanceHolder {
    private static final DefaultClock instance = new DefaultClock();
  }

  @NonNull
  public static Clock getInstance() {
    return InstanceHolder.instance;
  }

  public final long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public final long nanoTime() {
    return System.nanoTime();
  }

  public final long currentThreadTimeMillis() {
    return SystemClock.currentThreadTimeMillis();
  }

  public final long elapsedRealtime() {
    return SystemClock.elapsedRealtime();
  }
}
