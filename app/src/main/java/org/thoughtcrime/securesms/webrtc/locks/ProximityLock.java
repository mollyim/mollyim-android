package org.thoughtcrime.securesms.webrtc.locks;

import android.os.PowerManager;

import org.signal.core.util.logging.Log;

import java.util.Optional;

/**
 * Controls access to the proximity lock.
 * The proximity lock is not part of the public API.
 *
 * @author Stuart O. Anderson
*/
class ProximityLock {

  private static final String TAG = Log.tag(ProximityLock.class);

  private final Optional<PowerManager.WakeLock> proximityLock;

  ProximityLock(PowerManager pm) {
    proximityLock = getProximityLock(pm);
  }

  private Optional<PowerManager.WakeLock> getProximityLock(PowerManager pm) {
    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      return Optional.ofNullable(pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "signal:proximity"));
    } else {
      return Optional.empty();
    }
  }

  public void acquire() {
    if (!proximityLock.isPresent() || proximityLock.get().isHeld()) {
      return;
    }

    proximityLock.get().acquire();
  }

  public void release() {
    if (!proximityLock.isPresent() || !proximityLock.get().isHeld()) {
      return;
    }

    proximityLock.get().release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);

    Log.d(TAG, "Released proximity lock:" + proximityLock.get().isHeld());
  }
}
