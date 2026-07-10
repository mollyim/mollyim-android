/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.AppUtil
import org.signal.core.util.Result
import org.signal.core.util.StreamUtil
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RestoreDecision
import org.signal.registration.StorageController
import org.signal.registration.StoredProfileData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.RemoteRestoreResult
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.LinkedDeviceInfo
import org.thoughtcrime.securesms.database.model.databaseprotos.LocalRegistrationMetadata
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.NewAccount
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/**
 * Implementation of [StorageController] that bridges to the app's existing storage infrastructure.
 */
class AppRegistrationStorageController(private val context: Context) : StorageController {

  companion object {
    private val TAG = Log.tag(AppRegistrationStorageController::class)
    private const val TEMP_PROTO_FILENAME = "registration-in-progress.proto"
    private val MODERN_BACKUP_PATTERN = Regex("^signal-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")
    private val LEGACY_BACKUP_PATTERN = Regex("^signal-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})\\.backup$")
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.Default) {
    if (!SignalStore.account.isRegistered) {
      return@withContext null
    }

    val aci = SignalStore.account.aci ?: return@withContext null
    val pni = SignalStore.account.pni ?: return@withContext null
    val e164 = SignalStore.account.e164 ?: return@withContext null
    val servicePassword = SignalStore.account.servicePassword ?: return@withContext null
    val aep = SignalStore.account.accountEntropyPool

    val aciIdentityKeyPair = SignalStore.account.aciIdentityKey
    val pniIdentityKeyPair = SignalStore.account.pniIdentityKey

    PreExistingRegistrationData(
      e164 = e164,
      aci = aci,
      pni = pni,
      servicePassword = servicePassword,
      aep = aep,
      registrationLockEnabled = SignalStore.svr.isRegistrationLockEnabled,
      unrestrictedUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context),
      aciIdentityKeyPair = aciIdentityKeyPair,
      pniIdentityKeyPair = pniIdentityKeyPair
    )
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri()?.let { AppDependencies.blobs.delete(context, it) }
    SignalStore.registration.inProgressRegistrationDataBlobUri = null

    // Best-effort cleanup of the legacy plaintext file written by older builds.
    File(context.cacheDir, TEMP_PROTO_FILENAME).takeIf { it.exists() }?.delete()
    Unit
  }

  override suspend fun clearLocalDataAndRestart() = withContext(Dispatchers.Main) {
    Log.w(TAG, "[clearLocalDataAndRestart] Wiping all local app data and attempting to relaunch.")
    AppUtil.clearAllDataAndRestart(context)
  }

  override suspend fun getStoredProfileData(): StoredProfileData = withContext(Dispatchers.IO) {
    if (!SignalStore.account.isRegistered) {
      return@withContext StoredProfileData()
    }

    val self = Recipient.self()
    val profileName = self.profileName

    val avatar: ByteArray? = if (AvatarHelper.hasAvatar(context, self.id)) {
      try {
        AvatarHelper.getAvatar(context, self.id)?.use { StreamUtil.readFully(it) }
      } catch (e: IOException) {
        Log.w(TAG, "[getStoredProfileData] Failed to read self avatar.", e)
        null
      }
    } else {
      null
    }

    val discoverable: Boolean? = when (SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode) {
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.DISCOVERABLE -> true
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE -> false
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.UNDECIDED -> null
    }

    StoredProfileData(
      givenName = profileName.givenName,
      familyName = profileName.familyName,
      avatar = avatar,
      discoverableByPhoneNumber = discoverable
    )
  }

  override suspend fun readInProgressRegistrationData(): RegistrationData = withContext(Dispatchers.IO) {
    val uri = SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri() ?: return@withContext RegistrationData()
    try {
      AppDependencies.blobs.getStream(context, uri).use { RegistrationData.ADAPTER.decode(it) }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read/decode in-progress registration data, returning empty.", e)
      RegistrationData()
    }
  }

  override suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit) = withContext(Dispatchers.IO) {
    val current = readInProgressRegistrationData()
    val updated = current.newBuilder().apply(updater).build()
    writeRegistrationData(updated)
  }

  override suspend fun commitRegistrationData() = withContext(Dispatchers.IO) {
    val data = readInProgressRegistrationData()

    // The account's master key is always the one derived from the AEP, which we expect to have by the time we commit.
    // Restore it up-front so any master-key-derived state we touch below resolves against the correct value rather
    // than lazily generating a new AEP.
    val accountEntropyPool: AccountEntropyPool? = data.accountEntropyPool.takeIf { it.isNotEmpty() }?.let { AccountEntropyPool(it) }
    if (accountEntropyPool != null) {
      if (data.linkedDeviceData != null) {
        SignalStore.account.setAccountEntropyPoolFromPrimaryDevice(accountEntropyPool)
      } else {
        SignalStore.account.restoreAccountEntropyPool(accountEntropyPool)
      }
    }

    val masterKey: MasterKey? = accountEntropyPool?.deriveMasterKey()

    // Build LocalRegistrationMetadata if we have enough data for account setup
    if (data.e164.isNotEmpty() && data.aci.isNotEmpty() && data.pni.isNotEmpty() && data.servicePassword.isNotEmpty()) {
      val profileKey = RegistrationRepository.getProfileKey(data.e164)

      val metadata = LocalRegistrationMetadata.Builder().apply {
        if (data.aciIdentityKeyPair.size > 0) {
          aciIdentityKeyPair = data.aciIdentityKeyPair
        }
        if (data.pniIdentityKeyPair.size > 0) {
          pniIdentityKeyPair = data.pniIdentityKeyPair
        }
        if (data.aciSignedPreKey.size > 0) {
          aciSignedPreKey = data.aciSignedPreKey
        }
        if (data.pniSignedPreKey.size > 0) {
          pniSignedPreKey = data.pniSignedPreKey
        }
        if (data.aciLastResortKyberPreKey.size > 0) {
          aciLastRestoreKyberPreKey = data.aciLastResortKyberPreKey
        }
        if (data.pniLastResortKyberPreKey.size > 0) {
          pniLastRestoreKyberPreKey = data.pniLastResortKyberPreKey
        }

        aci = data.aci
        pni = data.pni
        e164 = data.e164
        this.servicePassword = data.servicePassword
        this.profileKey = profileKey.serialize().toByteString()
        hasPin = data.pin.isNotEmpty()
        if (data.pin.isNotEmpty()) {
          pin = data.pin
          masterKey?.let { this.masterKey = it.serialize().toByteString() }
        }
        fcmEnabled = SignalStore.account.fcmEnabled
        fcmToken = SignalStore.account.fcmToken ?: ""
        reglockEnabled = data.registrationLockEnabled

        data.linkedDeviceData?.let { linkData ->
          linkedDeviceInfo = LinkedDeviceInfo(
            deviceId = linkData.deviceId,
            deviceName = linkData.deviceName,
            ephemeralBackupKey = linkData.ephemeralBackupKey,
            accountEntropyPool = data.accountEntropyPool,
            mediaRootBackupKey = linkData.mediaRootBackupKey
          )
        }
      }.build()

      SignalStore.account.registrationId = data.aciRegistrationId
      SignalStore.account.pniRegistrationId = data.pniRegistrationId

      // TODO [greyson] Should probably move this stuff into this file as we get closer to being done
      RegistrationRepository.registerAccountLocally(context, metadata)
      SignalStore.registration.localRegistrationMetadata = metadata

      data.linkedDeviceData?.readReceipts?.let { TextSecurePreferences.setReadReceiptsEnabled(context, it) }
    }

    // Handle PIN/master key
    if (data.pin.isNotEmpty() && masterKey != null && data.linkedDeviceData == null) {
      SvrRepository.onRegistrationComplete(
        masterKey,
        data.pin,
        true,
        data.registrationLockEnabled,
        data.accountEntropyPool.isNotEmpty()
      )
    } else if (data.pinOptedOut && data.linkedDeviceData == null) {
      Log.i(TAG, "[commitRegistrationData] User opted out of creating a PIN. Applying opt-out.")
      SvrRepository.optOutOfPin(rotateAep = false)
    }

    // The temporaryMasterKey is the one-time key restored from SVR during re-registration. The account's own master key
    // is always the AEP-derived one above, so this is retained separately as the initial-restore key (used for the
    // first storage service sync + recovery password). It must be set last, as onRegistrationComplete will have cleared
    // the initial-restore key after recognizing the AEP-derived master key as our own.
    if (data.temporaryMasterKey.size > 0) {
      SignalStore.svr.masterKeyForInitialDataRestore = MasterKey(data.temporaryMasterKey.toByteArray())
    }

    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  override suspend fun setRestoreDecision(decision: RestoreDecision) = withContext(Dispatchers.Default) {
    if (!SignalStore.registration.restoreDecisionState.isDecisionPending) {
      return@withContext
    }

    SignalStore.registration.restoreDecisionState = when (decision) {
      RestoreDecision.NEW_ACCOUNT -> RestoreDecisionState.NewAccount
      RestoreDecision.SKIPPED -> RestoreDecisionState.Skipped
      RestoreDecision.COMPLETED -> RestoreDecisionState.Completed
    }
  }

  override fun restoreLocalBackupV1(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = flow {
    // TODO [greyson] better progress
    Log.d(TAG, "Starting V1 local backup restore from: $uri")

    emit(LocalBackupRestoreProgress.Preparing)

    try {
      if (!FullBackupImporter.validatePassphrase(context, uri, passphrase)) {
        emit(LocalBackupRestoreProgress.Error(IllegalArgumentException("Invalid passphrase")))
        return@flow
      }

      val database = SignalDatabase.backupDatabase
      FullBackupImporter.importFile(
        context,
        AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).getOrCreateAttachmentSecret(),
        database,
        uri,
        passphrase,
        SignalStore.registration.localRegistrationMetadata != null
      )

      SignalDatabase.runPostBackupRestoreTasks(database)

      emit(LocalBackupRestoreProgress.Complete)
      Log.d(TAG, "V1 restore complete.")
    } catch (e: FullBackupImporter.DatabaseDowngradeException) {
      Log.w(TAG, "V1 restore failed: database downgrade", e)
      emit(LocalBackupRestoreProgress.Error(e))
    } catch (e: Exception) {
      Log.w(TAG, "V1 restore failed", e)
      emit(LocalBackupRestoreProgress.Error(e))
    }
  }.flowOn(Dispatchers.IO)

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> = flow {
    // TODO [greyson] better progress
    Log.d(TAG, "Starting V2 local backup restore from backup=$backupUri, root=$rootUri")

    emit(LocalBackupRestoreProgress.Preparing)

    try {
      val backupDir = DocumentFile.fromTreeUri(context, backupUri)
      if (backupDir == null || !backupDir.canRead()) {
        emit(LocalBackupRestoreProgress.Error(IllegalStateException("Could not open backup directory")))
        return@flow
      }

      val selfAci = SignalStore.account.aci
      val selfPni = SignalStore.account.pni
      val selfE164 = SignalStore.account.e164

      if (selfAci == null || selfPni == null || selfE164 == null) {
        emit(LocalBackupRestoreProgress.Error(IllegalStateException("Account not registered, cannot restore V2 backup")))
        return@flow
      }

      val selfData = BackupRepository.SelfData(selfAci, selfPni, selfE164, ProfileKeyUtil.getSelfProfileKey())
      val messageBackupKey = aep.deriveMessageBackupKey()
      val snapshotFileSystem = SnapshotFileSystem(context, backupDir)

      when (val result = LocalArchiver.import(snapshotFileSystem, selfData, messageBackupKey)) {
        is Result.Success -> {
          emit(LocalBackupRestoreProgress.Complete)
          Log.d(TAG, "V2 restore complete.")
        }
        is Result.Failure -> {
          Log.w(TAG, "V2 restore failed: ${result.failure}")
          emit(LocalBackupRestoreProgress.Error(IOException("V2 restore failed: ${result.failure}")))
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "V2 restore failed", e)
      emit(LocalBackupRestoreProgress.Error(e))
    }
  }.flowOn(Dispatchers.IO)

  override suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = withContext(Dispatchers.IO) {
    val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
    val children = folder.listFiles()

    // If the selected folder contains a SignalBackups directory, use that instead
    val signalBackupsDir = children.firstOrNull { it.isDirectory && it.name == "SignalBackups" }
    val effectiveChildren = if (signalBackupsDir != null) {
      Log.d(TAG, "Found SignalBackups directory, using it as the effective folder")
      signalBackupsDir.listFiles()
    } else {
      children
    }

    val backups = mutableListOf<LocalBackupInfo>()

    // Check for modern backups: requires a 'files' directory and signal-backup-* directories
    val hasFilesDir = effectiveChildren.any { it.isDirectory && it.name == "files" }
    if (hasFilesDir) {
      for (child in effectiveChildren) {
        if (!child.isDirectory) continue
        val name = child.name ?: continue
        val match = MODERN_BACKUP_PATTERN.matchEntire(name) ?: continue
        val (year, month, day, hour, minute, second) = match.destructured
        try {
          val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
          backups.add(
            LocalBackupInfo(
              type = LocalBackupInfo.BackupType.V2,
              date = date,
              name = name,
              uri = child.uri
            )
          )
        } catch (e: Exception) {
          Log.w(TAG, "Failed to parse date from modern backup name: $name", e)
        }
      }
    }

    // Check for legacy backups: signal-yyyy-MM-dd-HH-mm-ss.backup files
    for (child in effectiveChildren) {
      if (!child.isFile) continue
      val name = child.name ?: continue
      val match = LEGACY_BACKUP_PATTERN.matchEntire(name) ?: continue
      val (year, month, day, hour, minute, second) = match.destructured
      try {
        val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
        backups.add(
          LocalBackupInfo(
            type = LocalBackupInfo.BackupType.V1,
            date = date,
            name = name,
            uri = child.uri,
            sizeBytes = child.length()
          )
        )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to parse date from legacy backup name: $name", e)
      }
    }

    backups.sortedByDescending { it.date }
  }

  override fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> = callbackFlow {
    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onRestoreEvent(event: RestoreV2Event) {
        val progress = when (event.type) {
          RestoreV2Event.Type.PROGRESS_DOWNLOAD -> RemoteBackupRestoreProgress.Downloading(event.count.inWholeBytes, event.estimatedTotalCount.inWholeBytes)
          RestoreV2Event.Type.PROGRESS_RESTORE -> RemoteBackupRestoreProgress.Restoring(event.count.inWholeBytes, event.estimatedTotalCount.inWholeBytes)
          RestoreV2Event.Type.PROGRESS_FINALIZING -> RemoteBackupRestoreProgress.Finalizing
        }
        trySend(progress)
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        when (val result = BackupRepository.restoreRemoteBackup()) {
          is RemoteRestoreResult.Success -> {
            send(
              RemoteBackupRestoreProgress.Complete(
                restoredSvrPin = SignalStore.svr.pin,
                restoredProfileKey = SignalDatabase.recipients.getRecord(result.selfRecipientId).profileKey?.let { ProfileKey(it) }
              )
            )
          }
          RemoteRestoreResult.NetworkError -> {
            send(RemoteBackupRestoreProgress.NetworkError())
          }
          RemoteRestoreResult.Canceled -> {
            send(RemoteBackupRestoreProgress.Canceled)
          }
          RemoteRestoreResult.Failure -> {
            if (SignalStore.backup.hasInvalidBackupVersion) {
              send(RemoteBackupRestoreProgress.InvalidBackupVersion)
            } else {
              send(RemoteBackupRestoreProgress.GenericError())
            }
          }
          RemoteRestoreResult.PermanentSvrBFailure -> {
            send(RemoteBackupRestoreProgress.PermanentSvrBFailure)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Remote restore failed", e)
        send(RemoteBackupRestoreProgress.GenericError(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  override fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress> = callbackFlow {
    val ephemeralBackupKeyBytes = SignalStore.registration.localRegistrationMetadata?.linkedDeviceInfo?.ephemeralBackupKey?.toByteArray()

    if (ephemeralBackupKeyBytes == null) {
      Log.i(TAG, "[restoreLinkAndSyncBackup] No ephemeral backup key present; nothing to restore.")
      trySend(LinkAndSyncProgress.Complete)
      channel.close()
      return@callbackFlow
    }

    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onRestoreEvent(event: RestoreV2Event) {
        val progress = when (event.type) {
          RestoreV2Event.Type.PROGRESS_DOWNLOAD -> LinkAndSyncProgress.Downloading(event.count, event.estimatedTotalCount)
          RestoreV2Event.Type.PROGRESS_RESTORE -> LinkAndSyncProgress.Restoring
          RestoreV2Event.Type.PROGRESS_FINALIZING -> LinkAndSyncProgress.Restoring
        }
        trySend(progress)
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        when (val result = BackupRepository.restoreLinkAndSyncBackup(TransferArchiveResponse(cdn = cdn, key = key), MessageBackupKey(ephemeralBackupKeyBytes))) {
          is RemoteRestoreResult.Success -> send(LinkAndSyncProgress.Complete)
          RemoteRestoreResult.Canceled -> Log.i(TAG, "[restoreLinkAndSyncBackup] Restore canceled.")
          else -> {
            Log.w(TAG, "[restoreLinkAndSyncBackup] Link-and-sync restore did not succeed: $result")
            send(LinkAndSyncProgress.Failed())
          }
        }
      } catch (e: CancellationException) {
        Log.d(TAG, "[restoreLinkAndSyncBackup] Restore cancelled, aborting.")
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Link-and-sync restore failed.", e)
        send(LinkAndSyncProgress.Failed(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val stamped = data.newBuilder().lastUpdatedMillis(System.currentTimeMillis()).build()

    val previousUri = SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri()
    val newUri = AppDependencies.blobs
      .forData(RegistrationData.ADAPTER.encode(stamped))
      .createForMultipleSessionsOnDisk(context)

    SignalStore.registration.inProgressRegistrationDataBlobUri = newUri.toString()
    previousUri?.let { AppDependencies.blobs.delete(context, it) }
    Unit
  }
}
