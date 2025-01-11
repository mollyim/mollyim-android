package org.thoughtcrime.securesms.registration.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.data.network.DeviceUuidRequestResult
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import java.net.URLEncoder

class LinkDeviceRepository(password: String) {

  private val context = AppDependencies.application

  private val accountManager: SignalServiceAccountManager =
    AccountManagerFactory.getInstance().createForDeviceLink(context, password)

  suspend fun requestDeviceLinkUuid(): DeviceUuidRequestResult =
    withContext(Dispatchers.IO) {
      val uuid = NetworkResult.fromFetch {
        accountManager.requestNewDeviceUuid()
      }
      return@withContext DeviceUuidRequestResult.from(uuid)
    }

  fun deviceLinkUrl(deviceUuid: String, devicePublicKey: IdentityKey): String =
    with(StringBuilder()) {
      append("sgnl://linkdevice?uuid=")
      append(
        URLEncoder.encode(deviceUuid, "utf-8")
      )
      append("&pub_key=")
      append(
        URLEncoder.encode(
          Base64.encodeWithoutPadding(devicePublicKey.publicKey.serialize()), "utf-8"
        )
      )
      toString()
    }

  suspend fun attemptDeviceLink(
    deviceKeyPair: IdentityKeyPair,
    deviceName: String,
    profileKey: ProfileKey,
    registrationId: Int,
    pniRegistrationId: Int,
    fcmToken: String?
  ): RegisterAccountResult =
    withContext(Dispatchers.IO) {
      val result = NetworkResult.fromFetch {
        accountManager.getNewDeviceRegistration(deviceKeyPair)
      }.then { registration ->
        val universalUnidentifiedAccess: Boolean = TextSecurePreferences.isUniversalUnidentifiedAccess(context)
        val unidentifiedAccessKey: ByteArray = UnidentifiedAccess.deriveAccessKeyFrom(profileKey)

        val registrationLock = registration.masterKey?.deriveRegistrationLock()
        val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(deviceName.toByteArray(), registration.aciIdentity)

        val notDiscoverable = SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE

        val accountAttributes = AccountAttributes(
          signalingKey = null,
          registrationId = registrationId,
          fetchesMessages = fcmToken.isNullOrBlank(),
          registrationLock = registrationLock,
          unidentifiedAccessKey = unidentifiedAccessKey,
          unrestrictedUnidentifiedAccess = universalUnidentifiedAccess,
          capabilities = AppCapabilities.getCapabilities(storageCapable = true),
          discoverableByPhoneNumber = !notDiscoverable,
          name = Base64.encodeWithPadding(encryptedDeviceName),
          pniRegistrationId = pniRegistrationId,
          recoveryPassword = null
        )

        val aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(
          registration.aciIdentity, SignalStore.account.aciPreKeys
        )
        val pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(
          registration.pniIdentity, SignalStore.account.pniPreKeys
        )

        NetworkResult.fromFetch {
          accountManager.finishNewDeviceRegistration(
            registration.provisioningCode,
            accountAttributes,
            aciPreKeyCollection, pniPreKeyCollection,
            fcmToken
          )
        }.map { deviceId ->
          // We have to set the deviceId before setting the identity keys, or they will throw for not being a linked device
          // But we also have to set the identity keys before calling registerAccountInternal, otherwise it will generate new ones when creating the prekeys
          SignalStore.account.deviceId = deviceId
          SignalStore.account.setDeviceName(deviceName)
          SignalStore.account.setAciIdentityKeysFromPrimaryDevice(registration.aciIdentity)
          SignalStore.account.setPniIdentityKeyAfterChangeNumber(registration.pniIdentity)
          SignalStore.account.hasLinkedDevices = true
          SignalStore.registration.hasUploadedProfile = true

          AccountRegistrationResult(
            uuid = registration.aci.toString(),
            pni = registration.pni.toString(),
            storageCapable = true,
            number = registration.number,
            masterKey = registration.masterKey,
            pin = null,
            aciPreKeyCollection = aciPreKeyCollection,
            pniPreKeyCollection = pniPreKeyCollection
          )
        }
      }

      return@withContext RegisterAccountResult.from(result)
    }

}
