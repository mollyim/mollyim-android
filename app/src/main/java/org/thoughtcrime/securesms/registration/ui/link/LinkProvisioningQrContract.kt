/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

import kotlinx.coroutines.flow.StateFlow
import org.whispersystems.signalservice.internal.push.ProvisionMessage

interface LinkProvisioningQrContract {
  val provisioningState: StateFlow<LinkProvisioningState>
  fun restartProvisioningSocket()
  fun clearProvisioningError()
}

data class LinkProvisioningState(
  val isRegistering: Boolean = false,
  val qrState: RegisterLinkDeviceQrViewModel.QrState = RegisterLinkDeviceQrViewModel.QrState.Loading,
  val provisionMessage: ProvisionMessage? = null,
  val hasProvisioningError: Boolean = false,
)

