package org.thoughtcrime.securesms.screenlock

import org.junit.Assert.*
import org.junit.Test

class ScreenLockSessionTest {
  private fun unlocked() = ScreenLockSession(100).apply { configure(true, true) }

  @Test fun coldStartRequiresAuthentication() {
    val session = ScreenLockSession(100)
    session.configure(true, false)
    assertTrue(session.resume(0))
  }

  @Test fun immediateLockAppliesEvenOnSameClockTick() {
    val session = unlocked()
    session.hide(100, 0)
    assertTrue(session.resume(100))
  }

  @Test fun returningBeforeDeadlineKeepsSessionOpen() {
    val session = unlocked()
    session.hide(100, 5_000)
    assertFalse(session.resume(5_099))
  }

  @Test fun exactDeadlineRequiresAuthentication() {
    val session = unlocked()
    session.hide(100, 5_000)
    assertTrue(session.resume(5_100))
  }

  @Test fun duplicateBackgroundEventsDoNotExtendGracePeriod() {
    val session = unlocked()
    session.hide(100, 5_000)
    session.hide(800, 5_000)
    assertTrue(session.resume(5_100))
  }

  @Test fun backgroundInvalidatesOutstandingAuthenticationEvenDuringGrace() {
    val session = unlocked()
    val token = session.authenticationGeneration
    session.hide(100, 5_000)
    session.resume(200)
    assertFalse(session.authenticate(token))
  }

  @Test fun authenticationCannotCompleteWhileBackgrounded() {
    val session = unlocked()
    session.hide(100, 0)
    assertFalse(session.authenticate(session.authenticationGeneration))
  }

  @Test fun configurationChangeInvalidatesAuthentication() {
    val session = unlocked()
    val token = session.authenticationGeneration
    session.configure(true, false)
    assertFalse(session.authenticate(token))
    assertTrue(session.locked)
  }

  @Test fun successfulAuthenticationCannotBeReplayed() {
    val session = ScreenLockSession(100).apply { configure(true, false) }
    val token = session.authenticationGeneration
    assertTrue(session.authenticate(token))
    assertFalse(session.authenticate(token))
    assertFalse(session.locked)
  }

  @Test fun databaseRelockCanForceIndependentScreenAuthentication() {
    val session = unlocked()
    session.forceLock()
    assertTrue(session.resume(100))
  }

  @Test fun databasePromptIsForegroundButRepeatedLockDoesNotInvalidateItsAuthentication() {
    val session = unlocked()
    session.hide(100, 0)
    session.forceLock()
    val token = session.authenticationGeneration
    session.forceLock()
    assertTrue(session.isCurrent(token))
    assertTrue(session.authenticate(token))
  }

  @Test fun disabledModeDoesNotLockOnBackground() {
    val session = ScreenLockSession(100).apply { configure(false, false) }
    session.hide(100, 0)
    assertFalse(session.resume(100))
  }

  @Test fun leavingDuringFirstSetupInvalidatesTheRequestWhileLockIsOff() {
    val session = ScreenLockSession(100).apply { configure(false, false) }
    val setup = session.authenticationGeneration
    session.hide(100, 0)
    assertFalse(session.isCurrent(setup))
    session.resume(101)
    assertFalse(session.isCurrent(setup))
    assertFalse(session.pendingLock)
  }

  @Test fun backgroundActionsCheckExpiryWithoutAnActivityResume() {
    val session = unlocked()
    session.hide(100, 5_000)
    val token = session.authenticationGeneration
    assertTrue(session.canRunAction(5_099, token))
    assertFalse(session.canRunAction(5_100, token))
    assertFalse(session.canRunAction(50_000, token))
  }

  @Test fun immediateLockBlocksNotificationAndSystemActionsOnSameTick() {
    val session = unlocked()
    session.hide(100, 0)
    assertFalse(session.canRunAction(100, session.authenticationGeneration))
  }

  @Test fun queuedActionsCannotCrossAnAppExitEvenBeforeTheDeadline() {
    val session = unlocked()
    val queued = session.authenticationGeneration
    session.hide(100, 5_000)
    assertFalse(session.canRunAction(101, queued))
  }

  @Test fun actionChecksDoNotConsumeOrExtendTheDeadline() {
    val session = unlocked()
    session.hide(100, 5_000)
    assertFalse(session.requiresAuthentication(200))
    assertFalse(session.requiresAuthentication(5_099))
    assertTrue(session.requiresAuthentication(5_100))
    assertTrue(session.resume(5_100))
  }

  @Test fun queuedActionsDoNotSurviveModeChangesOrAnotherAuthentication() {
    val session = unlocked()
    val token = session.authenticationGeneration
    session.configure(true, false)
    assertFalse(session.canRunAction(0, token))
    val authentication = session.authenticationGeneration
    assertTrue(session.authenticate(authentication))
    assertFalse(session.canRunAction(0, token))
    assertTrue(session.canRunAction(0, session.authenticationGeneration))
  }

  @Test fun foregroundSessionDoesNotExpireFromElapsedTimeAlone() {
    val session = unlocked()
    assertTrue(session.canRunAction(Long.MAX_VALUE, session.authenticationGeneration))
  }

  @Test fun disabledLockDoesNotCancelOrdinaryBackgroundWork() {
    val session = ScreenLockSession(100).apply { configure(false, false) }
    val queued = session.authenticationGeneration
    session.hide(100, 0)
    assertTrue(session.canRunAction(200, queued))
    assertFalse(session.isCurrent(queued))
    session.configure(true, false)
    assertFalse(session.canRunAction(201, queued))
  }

  @Test fun penaltyStartsAtFiveFailuresAndIsCapped() {
    assertEquals(0, ScreenLockBackoff.delayMillis(4))
    assertEquals(30_000, ScreenLockBackoff.delayMillis(5))
    assertEquals(60_000, ScreenLockBackoff.delayMillis(6))
    assertEquals(900_000, ScreenLockBackoff.delayMillis(10))
    assertEquals(900_000, ScreenLockBackoff.delayMillis(Int.MAX_VALUE))
  }
}
