/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class LocalBackupRestoreViewModel(
  private val repository: RegistrationRepository,
  parentState: Flow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val isPreRegistration: Boolean,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : EventDrivenViewModel<LocalBackupRestoreEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(LocalBackupRestoreViewModel::class)
  }

  private val _state = MutableStateFlow(LocalBackupRestoreState())
  val state = _state.asStateFlow()

  private var restoreJob: Job? = null

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(LocalBackupRestoreEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: LocalBackupRestoreEvents) {
    applyEvent(state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: LocalBackupRestoreState, event: LocalBackupRestoreEvents, stateEmitter: (LocalBackupRestoreState) -> Unit) {
    when (event) {
      is LocalBackupRestoreEvents.ParentStateChanged -> {
        stateEmitter(state.copy(storageCapable = event.state.storageCapable))
      }
      is LocalBackupRestoreEvents.PickBackupFolder -> {
        stateEmitter(state.copy(launchFolderPicker = true))
      }
      is LocalBackupRestoreEvents.BackupFolderSelected -> {
        stateEmitter(applyBackupFolderSelected(state, event.uri))
      }
      is LocalBackupRestoreEvents.RestoreBackup -> {
        applyRestoreBackup(state)
      }
      is LocalBackupRestoreEvents.PassphraseSubmitted -> {
        applyPassphraseSubmitted(state, event.credential, stateEmitter)
      }
      is LocalBackupRestoreEvents.ChooseDifferentFolder -> {
        stateEmitter(LocalBackupRestoreState(launchFolderPicker = true))
      }
      is LocalBackupRestoreEvents.BackupSelected -> {
        stateEmitter(state.copy(backupInfo = event.backup))
      }
      is LocalBackupRestoreEvents.FolderPickerDismissed -> {
        stateEmitter(state.copy(launchFolderPicker = false))
      }
      is LocalBackupRestoreEvents.Cancel -> {
        applyCancel(stateEmitter)
      }
    }
  }

  private fun applyBackupFolderSelected(state: LocalBackupRestoreState, uri: Uri): LocalBackupRestoreState {
    scanFolder(uri)
    return state.copy(
      launchFolderPicker = false,
      restorePhase = LocalBackupRestoreState.RestorePhase.Scanning,
      selectedFolderUri = uri
    )
  }

  private fun applyRestoreBackup(state: LocalBackupRestoreState) {
    val backup = state.backupInfo ?: return
    val credentialRoute = when (backup.type) {
      LocalBackupInfo.BackupType.V1 -> RegistrationRoute.EnterLocalBackupV1Passphrase
      LocalBackupInfo.BackupType.V2 -> RegistrationRoute.EnterAepForLocalBackup
    }
    parentEventEmitter.navigateTo(credentialRoute)
  }

  private fun applyPassphraseSubmitted(state: LocalBackupRestoreState, credential: String, stateEmitter: (LocalBackupRestoreState) -> Unit) {
    val backup = state.backupInfo ?: return
    val aep = if (backup.type == LocalBackupInfo.BackupType.V2) AccountEntropyPool(credential) else null
    val updatedState = when (backup.type) {
      LocalBackupInfo.BackupType.V1 -> state.copy(v1Passphrase = credential)
      LocalBackupInfo.BackupType.V2 -> state.copy(aep = aep)
    }
    stateEmitter(updatedState)
    startRestore(backup, state.selectedFolderUri, credential, aep)
  }

  private suspend fun onRestoreComplete(state: LocalBackupRestoreState, progress: LocalBackupRestoreProgress.Complete) {
    repository.persistRestoredBackupState(progress.restoredSvrPin, progress.restoredProfileKey)

    if (isPreRegistration) {
      repository.persistRestoredIdentityKeys(progress.restoredAciIdentityKey, progress.restoredPniIdentityKey)
      resultBus.sendResult(resultKey, LocalBackupRestoreResult.Success(state.aep))
      parentEventEmitter.navigateBack()
    } else {
      repository.setRestoreDecision(RestoreDecision.COMPLETED)

      if (progress.restoredSvrPin != null) {
        repository.restoreAccountRecord()
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      } else if (state.storageCapable) {
        parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
      } else {
        parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
      }
    }
  }

  private fun applyCancel(stateEmitter: (LocalBackupRestoreState) -> Unit) {
    restoreJob?.cancel()
    stateEmitter(LocalBackupRestoreState())
    if (isPreRegistration) {
      resultBus.sendResult(resultKey, LocalBackupRestoreResult.Canceled)
    }
    parentEventEmitter(RegistrationFlowEvent.NavigateBack)
  }

  private fun scanFolder(uri: Uri) {
    viewModelScope.launch {
      try {
        val backups = repository.scanLocalBackupFolder(uri)
        val mostRecent = backups.firstOrNull()
        if (mostRecent != null) {
          _state.value = LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
            backupInfo = mostRecent,
            allBackups = backups,
            selectedFolderUri = uri
          )
        } else {
          _state.value = LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.NoBackupFound,
            selectedFolderUri = uri
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error scanning backup folder", e)
        _state.value = LocalBackupRestoreState(
          restorePhase = LocalBackupRestoreState.RestorePhase.Error,
          errorMessage = e.message
        )
      }
    }
  }

  private fun startRestore(backup: LocalBackupInfo, rootUri: Uri?, credential: String, aep: AccountEntropyPool?) {
    restoreJob?.cancel()
    restoreJob = viewModelScope.launch {
      val currentState = _state.value
      val restoreFlow = when (backup.type) {
        LocalBackupInfo.BackupType.V1 -> repository.restoreV1Backup(backup.uri, passphrase = credential)
        LocalBackupInfo.BackupType.V2 -> repository.restoreV2Backup(rootUri = rootUri!!, backupUri = backup.uri, aep = aep!!)
      }
      restoreFlow.collect { progress ->
        _state.value = when (progress) {
          is LocalBackupRestoreProgress.Preparing -> LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.Preparing,
            aep = currentState.aep,
            v1Passphrase = currentState.v1Passphrase
          )
          is LocalBackupRestoreProgress.InProgress -> LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.InProgress,
            progressFraction = progress.progressFraction,
            aep = currentState.aep,
            v1Passphrase = currentState.v1Passphrase
          )
          is LocalBackupRestoreProgress.Complete -> {
            onRestoreComplete(_state.value.copy(aep = currentState.aep, v1Passphrase = currentState.v1Passphrase), progress)
            _state.value
          }
          is LocalBackupRestoreProgress.IncorrectCredential -> {
            Log.w(TAG, "Restore failed: incorrect passphrase/recovery key")
            currentState.copy(restorePhase = LocalBackupRestoreState.RestorePhase.IncorrectCredential)
          }
          is LocalBackupRestoreProgress.Error -> {
            Log.w(TAG, "Restore failed", progress.cause)
            LocalBackupRestoreState(
              restorePhase = LocalBackupRestoreState.RestorePhase.Error,
              errorMessage = progress.cause.message,
              aep = currentState.aep,
              v1Passphrase = currentState.v1Passphrase
            )
          }
        }
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: Flow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val isPreRegistration: Boolean,
    private val resultBus: ResultEventBus,
    private val resultKey: String
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LocalBackupRestoreViewModel(repository, parentState, parentEventEmitter, isPreRegistration, resultBus, resultKey) as T
    }
  }
}
