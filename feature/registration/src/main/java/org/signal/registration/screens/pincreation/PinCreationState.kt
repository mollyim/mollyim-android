/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.censor

data class PinCreationState(
  val isAlphanumericKeyboard: Boolean = false,
  val isConfirmEnabled: Boolean = false,
  val pinMismatch: Boolean = false,
  val firstPin: String? = null,
  val accountEntropyPool: AccountEntropyPool? = null
) {
  override fun toString(): String {
    return "PinCreationState(isAlphanumericKeyboard=$isAlphanumericKeyboard, isConfirmEnabled=$isConfirmEnabled, pinMismatch=$pinMismatch, firstPin=${firstPin?.let { "${it.length} chars" }}, accountEntropyPool=${accountEntropyPool?.displayValue?.censor()})"
  }
}
