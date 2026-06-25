/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

internal sealed interface DialogEvent {
  data class UsernameScanned(val qrCheckResult: MediaSendQrRepository.QrCheckResult.Username) : DialogEvent
  data object LinkedDeviceScanned : DialogEvent
}
