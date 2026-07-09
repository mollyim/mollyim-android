/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RestoreDecision
import org.signal.registration.StorageController
import org.signal.registration.StoredProfileData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress

/**
 * An in-memory [StorageController] representing a fresh, never-registered install.
 * Methods that should never be hit in the flows under test fail loudly.
 */
class FakeStorageController : StorageController {

  var inProgressData: RegistrationData = RegistrationData()
    private set
  var committedData: RegistrationData? = null
    private set
  var restoreDecision: RestoreDecision? = null
    private set

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = null

  override suspend fun clearAllData() {
    inProgressData = RegistrationData()
  }

  override suspend fun clearLocalDataAndRestart() = notExpected()

  override suspend fun readInProgressRegistrationData(): RegistrationData = inProgressData

  override suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit) {
    inProgressData = inProgressData.newBuilder().apply(updater).lastUpdatedMillis(System.currentTimeMillis()).build()
  }

  override suspend fun commitRegistrationData() {
    committedData = inProgressData
  }

  override suspend fun setRestoreDecision(decision: RestoreDecision) {
    restoreDecision = decision
  }

  override fun restoreLocalBackupV1(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = notExpected()

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> = notExpected()

  override fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> = notExpected()

  override fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress> = notExpected()

  override suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = notExpected()

  override suspend fun getStoredProfileData(): StoredProfileData = StoredProfileData()

  private fun notExpected(): Nothing {
    throw NotImplementedError("This method is not expected to be called in the flow under test.")
  }
}
