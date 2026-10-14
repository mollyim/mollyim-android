package org.thoughtcrime.securesms.screenlock

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenLockTimeoutsTest {
  @Test fun legacyAutomaticPolicyMigratesToImmediate() {
    assertEquals(0L, ScreenLockTimeouts.migrate(-1))
  }

  @Test fun explicitlyChosenTimesSurviveMigration() {
    for (seconds in ScreenLockTimeouts.seconds) assertEquals(seconds, ScreenLockTimeouts.migrate(seconds))
  }

  @Test(expected = IllegalArgumentException::class)
  fun unknownNegativeTimesAreRejected() { ScreenLockTimeouts.migrate(-2) }

  @Test(expected = IllegalArgumentException::class)
  fun unknownPositiveTimesAreRejected() { ScreenLockTimeouts.migrate(7) }
}
