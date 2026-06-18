/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

data class DeviceTransferInstructionsState(
  val oneTimeEvent: OneTimeEvent? = null
) {
  sealed interface OneTimeEvent
}
