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

  data object WrongNumber : VerificationCodeScreenEvents()

  data object ResendSms : VerificationCodeScreenEvents()

  data object CallMe : VerificationCodeScreenEvents()

  data object HavingTrouble : VerificationCodeScreenEvents()

  data object ConsumeInnerOneTimeEvent : VerificationCodeScreenEvents()

  /**
   * Event to update countdown timers. Should be triggered periodically (e.g., every second).
   */
  data object CountdownTick : VerificationCodeScreenEvents()
}
