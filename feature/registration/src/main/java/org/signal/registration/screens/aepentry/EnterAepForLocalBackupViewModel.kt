/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.util.navigateBack

class EnterAepForLocalBackupViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String,
  isPasswordManagerAvailable: Boolean = false
) : EventDrivenViewModel<EnterAepEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(EnterAepForLocalBackupViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<EnterAepState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: EnterAepEvents) {
    when (event) {
      is EnterAepEvents.BackupKeyChanged -> {
        _state.update { EnterAepScreenEventHandler.applyEvent(it, event) }
      }
      is EnterAepEvents.Submit -> {
        if (_state.value.isBackupKeyValid) {
          resultBus.sendResult(resultKey, _state.value.backupKey)
          parentEventEmitter.navigateBack()
        }
      }
      is EnterAepEvents.Cancel -> {
        parentEventEmitter.navigateBack()
      }
      is EnterAepEvents.DismissError -> {
        _state.update { EnterAepScreenEventHandler.applyEvent(it, event) }
      }
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String,
    private val isPasswordManagerAvailable: Boolean = false
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForLocalBackupViewModel(parentEventEmitter, resultBus, resultKey, isPasswordManagerAvailable) as T
    }
  }
}
