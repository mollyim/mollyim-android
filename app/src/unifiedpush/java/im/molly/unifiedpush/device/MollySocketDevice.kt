package im.molly.unifiedpush.device

import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.util.KeyHelper
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.account.PreKeyUpload
import org.whispersystems.signalservice.api.messages.multidevice.VerifyDeviceResponse
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import im.molly.unifiedpush.store.MollySocketStore
import java.io.IOException
import java.nio.charset.Charset

class MollySocketDevice {
  private val TAG = MollySocketDevice::class.java.simpleName
  private val DEVICE_NAME = "MollySocket"
  private val context = ApplicationDependencies.getApplication()
  private val store = MollySocketStore()

  var socketUri: String? = null

  init {
    if(!isMollySocketDevicePresent()) {
      Log.d(TAG, "MollySocketDevice is not present")
      store.removeUri()
    }
    socketUri = store.getUri()
      ?: run {
        newDevice()
        store.getUri()
      }
  }

  private fun isMollySocketDevicePresent(): Boolean {
    var devices : List<Device>? = emptyList()
    Thread {
      try {
        devices = DeviceListLoader(context, ApplicationDependencies.getSignalServiceAccountManager()).loadInBackground()
      } catch (e: IOException) {
        Log.e(TAG, "Encountered an IOException", e)
      }
    }.apply {
      start()
      join()
    }
    devices?.forEach { device ->
      if (device.id.toInt() == store.getDeviceId() && device.name == DEVICE_NAME) {
        return true
      }
    }
    return false
  }

  private fun newDevice() {
    Log.d(TAG, "Creating a device for MollySocket")

    Thread {
      try {
        val number = SignalStore.account().e164 ?: return@Thread
        val password = Util.getSecret(18)

        val verifyDeviceResponse = verifyNewDevice(number, password)
        TextSecurePreferences.setMultiDevice(context, true)

        generateAndRegisterPreKeys(number, verifyDeviceResponse.deviceId, password)
        store.saveDeviceId(verifyDeviceResponse.deviceId)
        store.saveUri(verifyDeviceResponse.uuid, verifyDeviceResponse.deviceId, password)
      } catch (e: IOException) {
        Log.e(TAG, "Encountered an IOException", e)
      }
    }.apply {
      start()
      join()
    }
  }

  @Throws(IOException::class)
  private fun verifyNewDevice(number: String, password: String): VerifyDeviceResponse {
    val verificationCode = ApplicationDependencies.getSignalServiceAccountManager()
      .newDeviceVerificationCode
    val registrationId = KeyHelper.generateRegistrationId(false)
    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(
      DEVICE_NAME.toByteArray(Charset.forName("UTF-8")),
      SignalStore.account().aciIdentityKey
    )

    return AccountManagerFactory.getInstance().createUnauthenticated(context, number, SignalServiceAddress.DEFAULT_DEVICE_ID, password)
      .verifySecondaryDevice(
        verificationCode,
        registrationId,
        true,
        "".toByteArray(),
        true,
        AppCapabilities.getCapabilities(true),
        false,
        encryptedDeviceName,
        SignalStore.account().pniRegistrationId
      )
  }

  @Throws(IOException::class)
  private fun generateAndRegisterPreKeys(number: String, deviceId: Int, password: String): Boolean? {
    val protocolStore = ApplicationDependencies.getProtocolStore().aci()
    val accountManager = AccountManagerFactory.getInstance().createAuthenticated(
      context,
      SignalStore.account().aci ?: return null,
      SignalStore.account().pni ?: return null,
      number,
      deviceId,
      password
    )
    val metadataStore = SignalStore.account().aciPreKeys
    val signedPreKey = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
    val oneTimePreKeys = PreKeyUtil.generateAndStoreOneTimeEcPreKeys(protocolStore, metadataStore)
    accountManager.setPreKeys(
      PreKeyUpload(
        serviceIdType = ServiceIdType.ACI,
        identityKey = protocolStore.identityKeyPair.publicKey,
        signedPreKey = signedPreKey,
        oneTimeEcPreKeys = oneTimePreKeys,
        lastResortKyberPreKey = null,
        oneTimeKyberPreKeys = null
      )
    )
    metadataStore.activeSignedPreKeyId = signedPreKey.id
    metadataStore.isSignedPreKeyRegistered = true
    return true
  }
}