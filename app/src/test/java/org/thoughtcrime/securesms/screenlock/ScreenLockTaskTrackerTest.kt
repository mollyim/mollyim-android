package org.thoughtcrime.securesms.screenlock

import org.junit.Assert.*
import org.junit.Test

class ScreenLockTaskTrackerTest {
  @Test fun firstTaskDoesNotNeedAnExistingTaskQuery() {
    assertFalse(ScreenLockTaskTracker().entering(10) { error("No previous task") })
  }

  @Test fun activityChangesWithinTheSameTaskDoNotEndAuthentication() {
    val tracker = ScreenLockTaskTracker()
    tracker.entering(10) { emptySet() }
    assertFalse(tracker.entering(10) { error("Same task") })
  }

  @Test fun switchingBetweenExistingAppTasksDoesNotEndAuthentication() {
    val tracker = ScreenLockTaskTracker()
    tracker.entering(10) { emptySet() }
    assertFalse(tracker.entering(20) { setOf(10, 20) })
    assertFalse(tracker.entering(10) { setOf(10, 20) })
  }

  @Test fun replacementOfTheRemovedTaskRequiresAuthentication() {
    val tracker = ScreenLockTaskTracker()
    tracker.entering(10) { emptySet() }
    assertTrue(tracker.entering(20) { setOf(20) })
    assertFalse(tracker.entering(20) { error("Already checked") })
  }

  @Test fun invalidTaskIdsDoNotForgetThePreviousTask() {
    val tracker = ScreenLockTaskTracker()
    tracker.entering(10) { emptySet() }
    assertFalse(tracker.entering(-1) { error("Invalid task") })
    assertTrue(tracker.entering(20) { setOf(20) })
  }

  @Test fun aMissingServiceCallbackCannotPreserveTheOldUnlockedSession() {
    val tracker = ScreenLockTaskTracker()
    val session = ScreenLockSession(100).apply { configure(true, true) }
    tracker.entering(10) { emptySet() }
    val oldAuthentication = session.authenticationGeneration
    session.hide(100, 600_000)

    // The key-caching service is absent: only entering the new task detects removal.
    if (tracker.entering(20) { setOf(20) }) session.end()

    assertTrue(session.resume(101))
    assertFalse(session.authenticate(oldAuthentication))
  }
}
