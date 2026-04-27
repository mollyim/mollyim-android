/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.EventDrivenViewModel

class DeviceTransferCompleteViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<DeviceTransferCompleteScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(DeviceTransferCompleteViewModel::class)
  }

  private val _state = MutableStateFlow(DeviceTransferCompleteState())
  val state: StateFlow<DeviceTransferCompleteState> = _state

  override suspend fun processEvent(event: DeviceTransferCompleteScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: DeviceTransferCompleteState,
    event: DeviceTransferCompleteScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (DeviceTransferCompleteState) -> Unit
  ) {
    when (event) {
      DeviceTransferCompleteScreenEvents.ContinueClicked -> {
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      }
      DeviceTransferCompleteScreenEvents.ConsumeOneTimeEvent -> {
        stateEmitter(state.copy(oneTimeEvent = null))
      }
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceTransferCompleteViewModel(parentEventEmitter) as T
    }
  }
}
