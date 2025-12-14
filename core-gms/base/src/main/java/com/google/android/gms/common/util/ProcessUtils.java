package com.google.android.gms.common.util;

import android.os.Process;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Keep
public class ProcessUtils {

  @Nullable
  private static Boolean cachedIsolated;

  private ProcessUtils() {}

  @NonNull
  public static String getMyProcessName() {
    return "";
  }

  public static boolean isIsolatedProcess() {
    if (cachedIsolated == null) {
      if (PlatformVersion.isAtLeastP()) {
        cachedIsolated = Process.isIsolated();
      } else {
        cachedIsolated = reflectIsIsolated();
      }
    }
    return cachedIsolated;
  }

  private static Boolean reflectIsIsolated() {
    try {
      return (Boolean) Process.class.getMethod("isIsolated").invoke(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
