package com.google.android.gms.stats;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class WakeLock {

  private final PowerManager.WakeLock pmWakeLock;

  public WakeLock(@NonNull Context context, int levelAndFlags, @NonNull String wakeLockName) {
    this.pmWakeLock = createWakeLock(context.getApplicationContext(), levelAndFlags, wakeLockName);
  }

  private static PowerManager.WakeLock createWakeLock(@NonNull Context context, int levelAndFlags, @NonNull String tag) {
    final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    return pm.newWakeLock(levelAndFlags, tag);
  }

  public void setReferenceCounted(boolean value) {
    pmWakeLock.setReferenceCounted(value);
  }

  public void acquire(long timeoutMillis) {
    pmWakeLock.acquire(timeoutMillis);
  }

  public void release() {
    pmWakeLock.release();
  }

  public boolean isHeld() {
    return pmWakeLock.isHeld();
  }
}
