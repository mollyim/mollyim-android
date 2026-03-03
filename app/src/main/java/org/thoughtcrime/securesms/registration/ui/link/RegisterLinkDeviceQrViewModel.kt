/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.registration.ui.provisioning.ProvisioningSocketController
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket

/**
 * Handles creating and maintaining a provisioning websocket in the pursuit
 * of adding this device as a linked device.
 */
class RegisterLinkDeviceQrViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RegisterLinkDeviceQrViewModel::class)
  }

  private val store: MutableStateFlow<RegisterLinkDeviceState> = MutableStateFlow(RegisterLinkDeviceState())

  val state: StateFlow<RegisterLinkDeviceState> = store

  private val provisioningController = ProvisioningSocketController<ProvisionMessage>(
    configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration(),
    scope = viewModelScope,
    mode = ProvisioningSocket.Mode.LINK
  ) { event ->
    handleProvisioningEvent(event)
  }

  init {
    restartProvisioningSocket()
  }

  override fun onCleared() {
    provisioningController.shutdown()
  }

  fun restartProvisioningSocket() {
    store.update {
      if (it.qrState !is QrState.Loaded) {
        it.copy(qrState = QrState.Loading)
      } else {
        it
      }
    }
    provisioningController.restart()
  }

  fun clearErrors() {
    store.update {
      it.copy(
        showProvisioningError = false,
        registrationErrorResult = null
      )
    }

    restartProvisioningSocket()
  }

  private fun handleProvisioningEvent(event: ProvisioningSocketController.Event<ProvisionMessage>) {
    when (event) {
      is ProvisioningSocketController.Event.QrReady -> {
        store.update {
          Log.d(TAG, "Updating QR code with data from [${event.socketId}]")
          it.copy(qrState = QrState.Loaded(event.qrData))
        }
      }

      is ProvisioningSocketController.Event.ProvisionMessageReady -> {
        store.update {
          it.copy(
            isRegistering = true,
            provisionMessage = event.message,
            qrState = QrState.Scanned
          )
        }
        provisioningController.shutdown()
      }

      is ProvisioningSocketController.Event.InvalidProvisioningPayload -> {
        store.update {
          it.copy(qrState = QrState.Scanned, showProvisioningError = true)
        }
      }

      is ProvisioningSocketController.Event.SocketFailed -> {
        store.update {
          Log.w(TAG, "Current socket [${event.socketId}] has failed, stopping automatic connects", event.throwable)
          provisioningController.shutdown()
          it.copy(qrState = QrState.Failed)
        }
      }
    }
  }

  fun setRegisterAsLinkedDeviceError(result: RegisterLinkDeviceResult) {
    store.update {
      it.copy(registrationErrorResult = result)
    }
  }

  data class RegisterLinkDeviceState(
    val isRegistering: Boolean = false,
    val qrState: QrState = QrState.Loading,
    val provisionMessage: ProvisionMessage? = null,
    val showProvisioningError: Boolean = false,
    val registrationErrorResult: RegisterLinkDeviceResult? = null
  )

  sealed interface QrState {
    data object Loading : QrState
    data class Loaded(val qrData: QrCodeData) : QrState
    data object Failed : QrState
    data object Scanned : QrState
  }
}
