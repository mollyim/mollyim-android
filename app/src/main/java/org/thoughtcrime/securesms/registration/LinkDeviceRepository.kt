package org.thoughtcrime.securesms.registration

import android.app.Application
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import java.io.IOException
import java.net.URLEncoder

/**
 * Link a new device to an existing primary device.
 */
class LinkDeviceRepository(private val context: Application) {

  fun requestDeviceLinkCode(
    registrationData: RegistrationData,
    deviceName: String?
  ): Single<LinkDeviceProgressProcessor> {
    Log.d(TAG, "Device link requested")

    val tempIdentityKey: IdentityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val accountManager: SignalServiceAccountManager = AccountManagerFactory.createForDeviceLink(
      context,
      registrationData.password
    )

    return Single.fromCallable<ServiceResponse<LinkDeviceProgress>> {
      try {
        ServiceResponse.forResult(
          LinkDeviceProgressImpl(
            "sgnl://linkdevice?uuid=" +
              URLEncoder.encode(accountManager.newDeviceUuid, "utf-8") +
              "&pub_key=" +
              URLEncoder.encode(Base64.encodeWithoutPadding(tempIdentityKey.publicKey.publicKey.serialize()), "utf-8"),
            tempIdentityKey,
            accountManager,
            registrationData,
            deviceName
          ),
          200, null
        )
      } catch (e: IOException) {
        ServiceResponse.forExecutionError(e)
      }
    }.subscribeOn(Schedulers.io()).map(::LinkDeviceProgressProcessor)
  }

  fun attemptDeviceLink(
    progress: LinkDeviceProgress
  ): Single<LinkDeviceResponseProcessor> {
    val progressImpl: LinkDeviceProgressImpl = progress as LinkDeviceProgressImpl
    val tempIdentityKey: IdentityKeyPair = progressImpl.tempIdentityKey
    val accountManager: SignalServiceAccountManager = progressImpl.accountManager
    val registrationData: RegistrationData = progressImpl.registrationData
    val deviceName: String? = progressImpl.deviceName

    return Single.fromCallable<ServiceResponse<LinkDeviceResponse>> {
      try {
        val ret = accountManager.getNewDeviceRegistration(tempIdentityKey)

        val universalUnidentifiedAccess: Boolean = TextSecurePreferences.isUniversalUnidentifiedAccess(context)
        val unidentifiedAccessKey: ByteArray = UnidentifiedAccess.deriveAccessKeyFrom(registrationData.profileKey)

        val registrationLock: String? = ret.masterKey?.deriveRegistrationLock()

        val encryptedDeviceName = deviceName?.let { DeviceNameCipher.encryptDeviceName(it.toByteArray(), ret.aciIdentity) }

        val accountAttributes = AccountAttributes(
          signalingKey = null,
          registrationId = registrationData.registrationId,
          fetchesMessages = registrationData.isNotFcm,
          registrationLock = registrationLock,
          unidentifiedAccessKey = unidentifiedAccessKey,
          unrestrictedUnidentifiedAccess = universalUnidentifiedAccess,
          capabilities = AppCapabilities.getCapabilities(true),
          discoverableByPhoneNumber = SignalStore.phoneNumberPrivacy().phoneNumberListingMode.isDiscoverable,
          name = encryptedDeviceName?.let { Base64.encodeWithPadding(it) },
          pniRegistrationId = registrationData.pniRegistrationId,
          recoveryPassword = registrationData.recoveryPassword
        )

        val aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(ret.aciIdentity, SignalStore.account().aciPreKeys)
        val pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(ret.pniIdentity, SignalStore.account().pniPreKeys)

        val deviceId = accountManager.finishNewDeviceRegistration(
          ret.provisioningCode,
          accountAttributes,
          aciPreKeyCollection, pniPreKeyCollection,
          registrationData.fcmToken
        )

        ServiceResponse.forResult(LinkDeviceResponse(deviceName, ret, deviceId, aciPreKeyCollection, pniPreKeyCollection), 200, null)
      } catch (e: IOException) {
        ServiceResponse.forExecutionError(e)
      }
    }.subscribeOn(Schedulers.io()).map(::LinkDeviceResponseProcessor)
  }

  companion object {
    private val TAG = Log.tag(LinkDeviceRepository::class.java)
  }

  class LinkDeviceProgressProcessor(response: ServiceResponse<LinkDeviceProgress>) : ServiceResponseProcessor<LinkDeviceProgress>(response) {
    fun asNewDeviceRegistrationReturnProcessor(): NewDeviceRegistrationReturnProcessor {
      return NewDeviceRegistrationReturnProcessor(ServiceResponse.coerceError(response))
    }
  }

  class LinkDeviceResponseProcessor(response: ServiceResponse<LinkDeviceResponse>) : ServiceResponseProcessor<LinkDeviceResponse>(response) {
    fun asNewDeviceRegistrationReturnProcessor(): NewDeviceRegistrationReturnProcessor {
      return NewDeviceRegistrationReturnProcessor(ServiceResponse.coerceError(response))
    }
  }

  class NewDeviceRegistrationReturnProcessor(response: ServiceResponse<LinkDeviceResponse>) : ServiceResponseProcessor<LinkDeviceResponse>(response)

  interface LinkDeviceProgress {
    val deviceLinkCode: String
  }

  private data class LinkDeviceProgressImpl(override val deviceLinkCode: String, val tempIdentityKey: IdentityKeyPair, val accountManager: SignalServiceAccountManager, val registrationData: RegistrationData, val deviceName: String?) : LinkDeviceProgress
}
