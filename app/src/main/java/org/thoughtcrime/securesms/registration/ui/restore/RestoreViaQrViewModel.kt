/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.provisioning.ProvisioningSocketController
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket

class RestoreViaQrViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RestoreViaQrViewModel::class)
  }

  private val store: MutableStateFlow<RestoreViaQrState> = MutableStateFlow(RestoreViaQrState())

  val state: StateFlow<RestoreViaQrState> = store

  private val provisioningController = ProvisioningSocketController<RegistrationProvisionMessage>(
    configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration(),
    scope = viewModelScope,
    mode = ProvisioningSocket.Mode.REREG
  ) { event ->
    handleProvisioningEvent(event)
  }

  init {
    restart()
  }

  fun restart() {
    SignalStore.registration.restoreMethodToken = null
    store.update {
      if (it.qrState !is QrState.Loaded) {
        it.copy(qrState = QrState.Loading)
      } else {
        it
      }
    }
    provisioningController.restart()
  }

  fun handleRegistrationFailure(registerAccountResult: RegisterAccountResult) {
    store.update {
      if (it.isRegistering) {
        Log.w(TAG, "Unable to register [${registerAccountResult::class.simpleName}]", registerAccountResult.getCause(), true)
        it.copy(
          isRegistering = false,
          provisioningMessage = null,
          showRegistrationError = true,
          registerAccountResult = registerAccountResult
        )
      } else {
        it
      }
    }
  }

  fun clearRegistrationError() {
    store.update {
      it.copy(
        showRegistrationError = false,
        registerAccountResult = null
      )
    }

    restart()
  }

  override fun onCleared() {
    provisioningController.shutdown()
  }

  private fun handleProvisioningEvent(event: ProvisioningSocketController.Event<RegistrationProvisionMessage>) {
    when (event) {
      is ProvisioningSocketController.Event.QrReady -> {
        store.update {
          Log.d(TAG, "Updating QR code with data from [${event.socketId}]", true)
          it.copy(qrState = QrState.Loaded(event.qrData))
        }
      }

      is ProvisioningSocketController.Event.ProvisionMessageReady -> {
        Log.d(TAG, "Received provisioning message result", true)
        Log.i(TAG, "Success! Saving restore method token: ***${event.message.restoreMethodToken.takeLast(4)}", true)
        SignalStore.registration.restoreMethodToken = event.message.restoreMethodToken
        SignalStore.registration.restoreBackupMediaSize = event.message.backupSizeBytes ?: 0
        SignalStore.registration.isOtherDeviceAndroid = event.message.platform == RegistrationProvisionMessage.Platform.ANDROID

        SignalStore.backup.lastBackupTime = event.message.backupTimestampMs ?: 0
        SignalStore.backup.isBackupTimestampRestored = true
        SignalStore.backup.restoringViaQr = true
        SignalStore.backup.backupTier = when (event.message.tier) {
          RegistrationProvisionMessage.Tier.FREE -> MessageBackupTier.FREE
          RegistrationProvisionMessage.Tier.PAID -> MessageBackupTier.PAID
          null -> null
        }

        store.update { it.copy(isRegistering = true, provisioningMessage = event.message, qrState = QrState.Scanned) }
        provisioningController.shutdown()
      }

      is ProvisioningSocketController.Event.InvalidProvisioningPayload -> {
        store.update {
          it.copy(showProvisioningError = true, qrState = QrState.Scanned)
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

  data class RestoreViaQrState(
    val isRegistering: Boolean = false,
    val qrState: QrState = QrState.Loading,
    val provisioningMessage: RegistrationProvisionMessage? = null,
    val showProvisioningError: Boolean = false,
    val showRegistrationError: Boolean = false,
    val registerAccountResult: RegisterAccountResult? = null
  )

  sealed interface QrState {
    data object Loading : QrState
    data class Loaded(val qrData: QrCodeData) : QrState
    data object Failed : QrState
    data object Scanned : QrState
  }
}
