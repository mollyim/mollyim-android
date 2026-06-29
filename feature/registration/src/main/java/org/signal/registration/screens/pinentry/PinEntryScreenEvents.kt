/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

sealed class PinEntryScreenEvents {
  data class PinEntered(val pin: String) : PinEntryScreenEvents() {
    override fun toString(): String = "PinEntered(pin=${pin.length} chars)"
  }
  data object ToggleKeyboard : PinEntryScreenEvents()
  data object NeedHelp : PinEntryScreenEvents()
  data object Skip : PinEntryScreenEvents()
  data object CreateNewPin : PinEntryScreenEvents()
  data object ContactSupport : PinEntryScreenEvents()
}
