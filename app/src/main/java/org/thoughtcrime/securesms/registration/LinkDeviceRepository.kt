package org.thoughtcrime.securesms.registration

import android.app.Application
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.SignalServiceAccountManager.NewDeviceRegistrationReturn
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.ConfirmCodeMessage
import org.whispersystems.util.Base64
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
              URLEncoder.encode(Base64.encodeBytesWithoutPadding(tempIdentityKey.publicKey.publicKey.serialize()), "utf-8"),
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
        val encryptedDeviceName = deviceName?.let { DeviceNameCipher.encryptDeviceName(it.toByteArray(), ret.aciIdentity) }
        val deviceId = accountManager.finishNewDeviceRegistration(
          ret.provisioningCode,
          ConfirmCodeMessage(
            false,
            true,
            registrationData.registrationId,
            registrationData.pniRegistrationId,
            Base64.encodeBytes(encryptedDeviceName),
            null
          )
        )
        ServiceResponse.forResult(LinkDeviceResponse(ret, deviceId), 200, null)
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

  class NewDeviceRegistrationReturnProcessor(response: ServiceResponse<NewDeviceRegistrationReturn>) : ServiceResponseProcessor<NewDeviceRegistrationReturn>(response)

  data class LinkDeviceResponse(val newDeviceRegistrationResponse: NewDeviceRegistrationReturn, val deviceId: Int)

  interface LinkDeviceProgress {
    val deviceLinkCode: String
  }

  private data class LinkDeviceProgressImpl(override val deviceLinkCode: String, val tempIdentityKey: IdentityKeyPair, val accountManager: SignalServiceAccountManager, val registrationData: RegistrationData, val deviceName: String?) : LinkDeviceProgress
}
