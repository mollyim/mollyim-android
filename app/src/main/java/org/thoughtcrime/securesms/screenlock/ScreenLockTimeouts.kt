package org.thoughtcrime.securesms.screenlock

/** Shared by both authentication methods; legacy automatic delays migrate to immediate. */
internal object ScreenLockTimeouts {
  val seconds = listOf(0L, 5L, 10L, 30L, 60L, 180L, 600L)

  fun migrate(value: Long): Long = (if (value == -1L) 0L else value).also { require(it in seconds) }
}
