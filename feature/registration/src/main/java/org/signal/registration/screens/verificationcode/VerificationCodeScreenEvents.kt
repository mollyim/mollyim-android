/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import org.signal.core.util.censor

sealed class VerificationCodeScreenEvents {
  data class CodeEntered(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeEntered(code=${code.censor()})"
  }

  /**
   * A verification code was automatically retrieved from an incoming SMS via the Play Services SMS retriever.
   */
  data class CodeAutoFilled(val code: String) : VerificationCodeScreenEvents() {
    override fun toString(): String = "CodeAutoFilled(code=${code.censor()})"
  }

  data object ConsumeAutoFillCode : VerificationCodeScreenEvents()

  data object WrongNumber : VerificationCodeScreenEvents()

  data object ResendSms : VerificationCodeScreenEvents()

  data object CallMe : VerificationCodeScreenEvents()

  data object HavingTrouble : VerificationCodeScreenEvents()

  data object ConsumeInnerOneTimeEvent : VerificationCodeScreenEvents()

  /**
   * Event to update countdown timers. Should be triggered periodically (e.g., every second).
   */
  data object CountdownTick : VerificationCodeScreenEvents()

  /**
   * The screen returned to the foreground. Used to check whether the in-progress registration data (and thus the
   * verification session) has grown too stale to keep waiting on, in which case we restart the flow.
   */
  data object Foregrounded : VerificationCodeScreenEvents()
}
