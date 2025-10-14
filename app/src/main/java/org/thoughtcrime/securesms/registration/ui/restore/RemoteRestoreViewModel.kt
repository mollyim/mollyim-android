/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RemoteRestoreResult
import org.thoughtcrime.securesms.backup.v2.RestoreTimestampResult
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.provisioning.RestoreMethod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class RemoteRestoreViewModel(isOnlyRestoreOption: Boolean) : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteRestoreViewModel::class)
  }

  private val store: MutableStateFlow<ScreenState> = MutableStateFlow(
    ScreenState(
      isRemoteRestoreOnlyOption = isOnlyRestoreOption,
      backupTier = SignalStore.backup.backupTier,
      backupTime = SignalStore.backup.lastBackupTime,
      backupSize = SignalStore.registration.restoreBackupMediaSize.bytes
    )
  )

  val state: StateFlow<ScreenState> = store.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch(Dispatchers.IO) {
      store.update { it.copy(loadState = ScreenState.LoadState.LOADING, loadAttempts = it.loadAttempts + 1) }
      val result = BackupRepository.restoreBackupFileTimestamp()
      store.update {
        when (result) {
          is RestoreTimestampResult.Success -> {
            it.copy(
              loadState = ScreenState.LoadState.LOADED,
              backupTier = SignalStore.backup.backupTier,
              backupTime = SignalStore.backup.lastBackupTime,
              backupSize = SignalStore.registration.restoreBackupMediaSize.bytes
            )
          }

          is RestoreTimestampResult.NotFound -> {
            it.copy(loadState = ScreenState.LoadState.NOT_FOUND)
          }

          else -> {
            if (it.loadState == ScreenState.LoadState.LOADING) {
              it.copy(loadState = ScreenState.LoadState.FAILURE)
            } else {
              it
            }
          }
        }
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      val config = BackupRepository.getBackupLevelConfiguration()
      if (config is NetworkResult.Success) {
        store.update {
          it.copy(backupMediaTTL = config.result.mediaTtlDays.days)
        }
      }
    }
  }

  fun restore() {
    viewModelScope.launch {
      store.update { it.copy(importState = ImportState.InProgress) }

      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.REMOTE_BACKUP)

        when (val result = BackupRepository.restoreRemoteBackup()) {
          RemoteRestoreResult.Success -> {
            Log.i(TAG, "Restore successful", true)
            SignalStore.registration.restoreDecisionState = RestoreDecisionState.Completed

            StorageServiceRestore.restore()

            store.update { it.copy(importState = ImportState.Restored) }
          }

          RemoteRestoreResult.NetworkError -> {
            Log.w(TAG, "Restore failed to download", true)
            store.update { it.copy(importState = ImportState.NetworkFailure) }
          }

          RemoteRestoreResult.Canceled,
          RemoteRestoreResult.Failure -> {
            Log.w(TAG, "Restore failed with $result", true)
            store.update { it.copy(importState = ImportState.Failed) }
          }

          RemoteRestoreResult.PermanentSvrBFailure -> {
            Log.w(TAG, "Hit a permanent SVRB error.", true)
            store.update { it.copy(importState = ImportState.FailureWithLogPrompt) }
          }
        }
      }
    }
  }

  fun updateRestoreProgress(restoreEvent: RestoreV2Event) {
    store.update { it.copy(restoreProgress = restoreEvent) }
  }

  fun cancel() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
  }

  fun clearError() {
    store.update { it.copy(importState = ImportState.None, restoreProgress = null) }
  }

  fun skipRestore() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.DECLINE)
      }
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (SignalStore.account.restoredAccountEntropyPool || SignalStore.svr.masterKeyForInitialDataRestore != null) {
      store.update { it.copy(loadState = ScreenState.LoadState.STORAGE_SERVICE_RESTORE) }
      StorageServiceRestore.restore()
    }
  }

  data class ScreenState(
    val isRemoteRestoreOnlyOption: Boolean = false,
    val backupMediaTTL: Duration = 30.days,
    val backupTier: MessageBackupTier? = null,
    val backupTime: Long = -1,
    val backupSize: ByteSize = 0.bytes,
    val importState: ImportState = ImportState.None,
    val restoreProgress: RestoreV2Event? = null,
    val loadState: LoadState = if (backupTier != null) LoadState.LOADED else LoadState.LOADING,
    val loadAttempts: Int = 0
  ) {

    fun isLoaded(): Boolean {
      return loadState == LoadState.LOADED
    }

    enum class LoadState {
      LOADING, LOADED, NOT_FOUND, FAILURE, STORAGE_SERVICE_RESTORE
    }
  }

  sealed interface ImportState {
    data object None : ImportState
    data object InProgress : ImportState
    data object Restored : ImportState
    data object NetworkFailure : ImportState
    data object Failed : ImportState
    data object FailureWithLogPrompt : ImportState
  }
}
