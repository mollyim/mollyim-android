package im.molly.unifiedpush.device

import im.molly.unifiedpush.model.MollyDevice
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
import java.io.IOException
import java.nio.charset.Charset

class MollySocketLinkedDevice {
  private val TAG = Log.tag(MollySocketLinkedDevice::class.java)
  private val DEVICE_NAME = "MollySocket"
  private val context = ApplicationDependencies.getApplication()

  var device: MollyDevice? = null

  init {
    if (isDeviceLinked() == false) {
      // If we previously had a linked device, it is no longer registered:
      // we remove information about this potential previous device
      Log.d(TAG, "MollySocketDevice is not present")
      SignalStore.unifiedpush().device = null
    }
    device = SignalStore.unifiedpush().device
      ?: run {
        newDevice()
        SignalStore.unifiedpush().device
      }
  }

  private fun isDeviceLinked(): Boolean? {
    val device = SignalStore.unifiedpush().device ?: return false
    val devices: List<Device>?
    try {
      devices = DeviceListLoader(context, ApplicationDependencies.getSignalServiceAccountManager()).loadInBackground()
    } catch (e: IOException) {
      Log.e(TAG, "Encountered an IOException", e)
      return null
    }
    devices?.forEach { it_device ->
      if (it_device.id.toInt() == device.deviceId && it_device.name == DEVICE_NAME) {
        return true
      }
    }
    return false
  }

  private fun newDevice() {
    Log.d(TAG, "Creating a device for MollySocket")
    try {
      val number = SignalStore.account().e164 ?: return
      val password = Util.getSecret(18)

      val verifyDeviceResponse = verifyNewDevice(number, password)
      TextSecurePreferences.setMultiDevice(context, true)

      generateAndRegisterPreKeys(number, verifyDeviceResponse.deviceId, password)
      SignalStore.unifiedpush().device = MollyDevice(
        uuid = verifyDeviceResponse.uuid.toString(),
        deviceId = verifyDeviceResponse.deviceId,
        password = password
      )
    } catch (e: IOException) {
      Log.e(TAG, "Encountered an IOException", e)
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
