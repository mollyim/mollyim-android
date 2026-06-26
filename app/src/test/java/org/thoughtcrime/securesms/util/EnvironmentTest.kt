package org.thoughtcrime.securesms.util

import org.junit.Assert.assertFalse
import org.junit.Test

class EnvironmentTest {
  @Test
  fun `IS_LINK_AND_SYNC_AVAILABLE must be false for release`() {
    assertFalse("IS_LINK_AND_SYNC_AVAILABLE must not be committed as true!", Environment.IS_LINK_AND_SYNC_AVAILABLE)
  }
}
