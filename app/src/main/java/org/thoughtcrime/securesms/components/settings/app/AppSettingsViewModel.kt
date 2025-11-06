package org.thoughtcrime.securesms.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class AppSettingsViewModel : ViewModel() {

  private val store = Store(
    AppSettingsState(
      isPrimaryDevice = SignalStore.account.isPrimaryDevice,
      unreadPaymentsCount = 0,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !SignalStore.account.isRegistered,
      isLinkedDevice = SignalStore.account.isLinkedDevice,
      clientDeprecated = SignalStore.misc.isClientDeprecated
    )
  )

  private val disposables = CompositeDisposable()

  val state: LiveData<AppSettingsState> = store.stateLiveData
  val self: LiveData<BioRecipientState> = Recipient.self().live().liveData.map { BioRecipientState(it) }

  override fun onCleared() {
    disposables.clear()
  }

  fun refreshDeprecatedOrUnregistered() {
    store.update {
      it.copy(
        clientDeprecated = SignalStore.misc.isClientDeprecated,
        userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !SignalStore.account.isRegistered
      )
    }
  }

  fun refresh() {
    store.update {
      it.copy(
        backupFailureState = getBackupFailureState()
      )
    }
  }

  private fun getBackupFailureState(): BackupFailureState {
    return if (BackupRepository.shouldDisplayOutOfRemoteStorageSpaceUx()) {
      BackupFailureState.OUT_OF_STORAGE_SPACE
    } else if (BackupRepository.shouldDisplayBackupFailedSettingsRow()) {
      BackupFailureState.BACKUP_FAILED
    } else if (BackupRepository.shouldDisplayCouldNotCompleteBackupSettingsRow()) {
      BackupFailureState.COULD_NOT_COMPLETE_BACKUP
    } else if (SignalStore.backup.subscriptionStateMismatchDetected) {
      BackupFailureState.SUBSCRIPTION_STATE_MISMATCH
    } else if (SignalStore.backup.hasBackupAlreadyRedeemedError) {
      BackupFailureState.ALREADY_REDEEMED
    } else {
      BackupFailureState.NONE
    }
  }
}
