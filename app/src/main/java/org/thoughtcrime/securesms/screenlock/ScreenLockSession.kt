package org.thoughtcrime.securesms.screenlock

/** Time and authentication state, independent of Android activity or database lifecycles. */
internal class ScreenLockSession(private var generation: Long) {
  var enabled = false
    private set
  var locked = false
    private set
  private var deadline: Long? = null
  private var background = false

  val authenticationGeneration: Long get() = generation
  val pendingLock: Boolean get() = locked || deadline != null

  fun configure(enabled: Boolean, authenticated: Boolean) {
    this.enabled = enabled
    locked = enabled && !authenticated
    deadline = null
    background = false
    generation++
  }

  fun hide(now: Long, timeoutMillis: Long) {
    if (background) return
    background = true
    generation++
    if (enabled && !locked) deadline = now + timeoutMillis.coerceAtLeast(0)
  }

  /** Checks expiry without consuming the background deadline or authorizing a callback. */
  fun requiresAuthentication(now: Long): Boolean = enabled && (locked || deadline?.let { now >= it } == true)

  fun canRunAction(now: Long, token: Long): Boolean = !enabled || (token == generation && !requiresAuthentication(now))

  fun resume(now: Long): Boolean {
    locked = requiresAuthentication(now)
    deadline = null
    background = false
    return locked
  }

  fun forceLock() {
    if (!enabled) return
    val changed = !locked || deadline != null || background
    locked = true
    deadline = null
    background = false
    if (changed) generation++
  }

  fun isCurrent(token: Long): Boolean = token == generation && !background

  fun authenticate(token: Long): Boolean {
    if (!isCurrent(token)) return false
    locked = false
    deadline = null
    generation++
    return true
  }
}

internal object ScreenLockBackoff {
  fun delayMillis(failures: Int): Long =
    if (failures < 5) 0 else (30_000L shl (failures - 5).coerceIn(0, 5)).coerceAtMost(900_000L)
}
