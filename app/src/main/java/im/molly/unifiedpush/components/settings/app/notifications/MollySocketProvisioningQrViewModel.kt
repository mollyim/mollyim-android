/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import im.molly.unifiedpush.MollySocketRepository
import im.molly.unifiedpush.model.MollySocketDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.registration.ui.provisioning.ProvisioningSocketController
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.internal.push.ProvisionMessage

class MollySocketProvisioningQrViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(MollySocketProvisioningQrViewModel::class)
  }

  private val store = MutableStateFlow(State())
  val state: StateFlow<State> = store

  private var provisioningJob: Job? = null
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
    shutdown()
  }

  fun restartProvisioningSocket() {
    shutdown()
    store.update { it.copy(qrState = QrState.Loading, error = null, device = null) }
    provisioningController.restart()
  }

  fun clearErrorAndRetry() {
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
        val verificationCode = event.message.provisioningCode
        if (verificationCode.isNullOrBlank()) {
          store.update {
            it.copy(
              qrState = QrState.Failed,
              error = "Missing verification code.",
              isRegistering = false
            )
          }
          return
        }

        store.update { it.copy(qrState = QrState.Scanned, isRegistering = true, error = null) }
        provisioningController.shutdown()

        provisioningJob = viewModelScope.launch(Dispatchers.IO) {
          runCatching {
            MollySocketRepository.createDeviceFromVerificationCode(verificationCode)
          }.onSuccess { device ->
            store.update { it.copy(device = device, isRegistering = false) }
          }.onFailure { throwable ->
            Log.w(TAG, "Unable to create MollySocket device", throwable)
            store.update {
              it.copy(
                qrState = QrState.Failed,
                isRegistering = false,
                error = throwable.message ?: "Unable to create linked credential."
              )
            }
          }
        }
      }

      is ProvisioningSocketController.Event.InvalidProvisioningPayload -> {
        store.update {
          it.copy(
            qrState = QrState.Failed,
            isRegistering = false,
            error = "Invalid provisioning payload."
          )
        }
      }

      is ProvisioningSocketController.Event.SocketFailed -> {
        store.update {
          Log.w(TAG, "Current socket [${event.socketId}] has failed, stopping automatic connects", event.throwable)
          provisioningController.shutdown()
          it.copy(
            qrState = QrState.Failed,
            isRegistering = false
          )
        }
      }
    }
  }

  private fun shutdown() {
    provisioningJob?.cancel()
    provisioningJob = null
    provisioningController.shutdown()
  }

  data class State(
    val qrState: QrState = QrState.Loading,
    val isRegistering: Boolean = false,
    val error: String? = null,
    val device: MollySocketDevice? = null,
  )

  sealed interface QrState {
    data object Loading : QrState
    data class Loaded(val qrData: QrCodeData) : QrState
    data object Scanned : QrState
    data object Failed : QrState
  }
}

