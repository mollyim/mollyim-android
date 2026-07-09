/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import kotlinx.coroutines.flow.Flow
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.RequestResult
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CheckSvrCredentialsError
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.LinkDeviceProvisioningEvent
import org.signal.registration.NetworkController.LinkDeviceResponse
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegisterAsLinkedDeviceError
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreAccountRecordError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.RestoreMethod
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetProfileError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.NetworkController.SetRestoreMethodError
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration

/**
 * An in-memory [NetworkController] that plays the part of a well-behaved server for a fresh registration.
 * Methods that should never be hit in the flows under test fail loudly.
 */
class FakeNetworkController(
  private val correctVerificationCode: String = "123456"
) : NetworkController {

  val sessionId = "fake-session-id"

  var sessionCreated = false
    private set
  var verificationCodeRequested = false
    private set
  var registeredE164: String? = null
    private set
  var svrPin: String? = null
    private set
  var svrMasterKey: MasterKey? = null
    private set
  var accountAttributesSyncJobEnqueued = false
    private set

  private var verified = false

  private fun currentSession(): SessionMetadata {
    return SessionMetadata(
      id = sessionId,
      nextSms = null,
      nextCall = null,
      nextVerificationAttempt = null,
      allowedToRequestCode = true,
      requestedInformation = emptyList(),
      verified = verified
    )
  }

  override suspend fun createSession(e164: String, fcmToken: String?, mcc: String?, mnc: String?): RequestResult<SessionMetadata, CreateSessionError> {
    sessionCreated = true
    return RequestResult.Success(currentSession())
  }

  override suspend fun getSession(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError> {
    return RequestResult.Success(currentSession())
  }

  override suspend fun updateSession(sessionId: String?, pushChallengeToken: String?, captchaToken: String?): RequestResult<SessionMetadata, UpdateSessionError> {
    return RequestResult.Success(currentSession())
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> {
    verificationCodeRequested = true
    return RequestResult.Success(currentSession())
  }

  override suspend fun submitVerificationCode(sessionId: String, verificationCode: String): RequestResult<SessionMetadata, SubmitVerificationCodeError> {
    if (verificationCode == correctVerificationCode) {
      verified = true
    }
    return RequestResult.Success(currentSession())
  }

  override suspend fun registerAccount(
    e164: String,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> {
    check(verified) { "Attempted to register before the session was verified!" }
    registeredE164 = e164

    return RequestResult.Success(
      RegisterAccountResponse(
        aci = UUID.randomUUID().toString(),
        pni = UUID.randomUUID().toString(),
        e164 = e164,
        usernameHash = null,
        usernameLinkHandle = null,
        storageCapable = false,
        entitlements = null,
        reregistration = false
      )
    )
  }

  override suspend fun getFcmToken(): String? = null

  override suspend fun awaitPushChallengeToken(): String? = null

  override fun getCaptchaUrl(): String = "https://example.com/captcha"

  override suspend fun restoreMasterKeyFromSvr(svrCredentials: SvrCredentials, pin: String): RequestResult<MasterKeyResponse, RestoreMasterKeyError> {
    notExpected()
  }

  override suspend fun setPinAndMasterKeyOnSvr(pin: String, masterKey: MasterKey): RequestResult<SvrCredentials?, BackupMasterKeyError> {
    svrPin = pin
    svrMasterKey = masterKey
    return RequestResult.Success(null)
  }

  override suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean = true

  override suspend fun enableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = notExpected()

  override suspend fun disableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = notExpected()

  override suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError> = notExpected()

  override suspend fun checkSvrCredentials(e164: String, credentials: List<SvrCredentials>): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> = notExpected()

  override suspend fun setAccountAttributes(attributes: AccountAttributes): RequestResult<Unit, SetAccountAttributesError> = notExpected()

  override suspend fun enqueueAccountAttributesSyncJob() {
    accountAttributesSyncJobEnqueued = true
  }

  override suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<GetBackupInfoResponse, GetBackupInfoError> = notExpected()

  override suspend fun getBackupFileLastModified(aep: AccountEntropyPool, backupInfo: GetBackupInfoResponse): RequestResult<Long, GetBackupInfoError> = notExpected()

  override suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, NetworkController.VerifyBackupKeyError> = notExpected()

  override fun startProvisioning(): Flow<ProvisioningEvent> = notExpected()

  override fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<LinkDeviceProvisioningEvent> = notExpected()

  override suspend fun registerAsLinkedDevice(
    e164: String,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?
  ): RequestResult<LinkDeviceResponse, RegisterAsLinkedDeviceError> = notExpected()

  override suspend fun onLinkedDeviceRegistered() = notExpected()

  override suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult = notExpected()

  override suspend fun restoreLinkedDeviceFromStorageService() = notExpected()

  override fun startNewDeviceTransferServer(context: android.content.Context, aep: AccountEntropyPool) = notExpected()

  override suspend fun setRestoreMethod(token: String, method: RestoreMethod): RequestResult<Unit, SetRestoreMethodError> = notExpected()

  override suspend fun restoreAccountRecord(timeout: Duration): RequestResult<Unit, RestoreAccountRecordError> {
    return RequestResult.Success(Unit)
  }

  override suspend fun setProfile(givenName: String, familyName: String, avatar: ByteArray?, discoverableByPhoneNumber: Boolean): RequestResult<Unit, SetProfileError> {
    return RequestResult.Success(Unit)
  }

  private fun notExpected(): Nothing {
    throw NotImplementedError("This method is not expected to be called in the flow under test.")
  }
}
