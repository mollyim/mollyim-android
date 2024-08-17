package im.molly.unifiedpush.device

import android.content.Context
import im.molly.unifiedpush.model.MollyDevice
import im.molly.unifiedpush.util.UnifiedPushNotificationBuilder
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.util.KeyHelper
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import java.io.IOException
import java.nio.charset.Charset

class MollySocketLinkedDevice(val context: Context) {
  private val TAG = Log.tag(MollySocketLinkedDevice::class.java)
  private val DEVICE_NAME = "MollySocket"

  var device: MollyDevice? = null

  init {
    if (isDeviceLinked() == false) {
      // If we previously had a linked device, it is no longer registered:
      // we remove information about this potential previous device
      Log.d(TAG, "MollySocketDevice is not present")
      SignalStore.unifiedpush.device = null
    }
    device = SignalStore.unifiedpush.device
      ?: run {
        newDevice()
        SignalStore.unifiedpush.device
      }
  }

  private fun isDeviceLinked(): Boolean? {
    val device = SignalStore.unifiedpush.device ?: return false
    val devices: List<Device>?
    try {
      devices = DeviceListLoader(context, AppDependencies.signalServiceAccountManager).loadInBackground()
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
      val number = SignalStore.account.e164 ?: return
      val password = Util.getSecret(18)

      val deviceId = verifyNewDevice(number, password)
      TextSecurePreferences.setMultiDevice(context, true)

      SignalStore.unifiedpush.device = MollyDevice(
        uuid = SignalStore.account.aci.toString(),
        deviceId = deviceId,
        password = password
      )
    } catch (e: DeviceLimitExceededException) {
      Log.w(TAG, "The account already have 5 linked devices.")
      UnifiedPushNotificationBuilder(context).setNotificationDeviceLimitExceeded()
    } catch (e: IOException) {
      Log.e(TAG, "Encountered an IOException", e)
    }
  }

  @Throws(IOException::class)
  private fun verifyNewDevice(number: String, password: String): Int {
    val verificationCode = AppDependencies.signalServiceAccountManager
      .newDeviceVerificationCode
    val registrationId = KeyHelper.generateRegistrationId(false)
    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(
      DEVICE_NAME.toByteArray(Charset.forName("UTF-8")),
      SignalStore.account.aciIdentityKey
    )
    val accountManager = AccountManagerFactory.getInstance().createUnauthenticated(context, number, SignalServiceAddress.DEFAULT_DEVICE_ID, password)

    val accountAttributes = AccountAttributes(
      signalingKey = null,
      registrationId = registrationId,
      fetchesMessages = true,
      registrationLock = null,
      unidentifiedAccessKey = null,
      unrestrictedUnidentifiedAccess = true,
      capabilities = AppCapabilities.getCapabilities(true),
      discoverableByPhoneNumber =
        SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.DISCOVERABLE,
      name = Base64.encodeWithPadding(encryptedDeviceName),
      pniRegistrationId = SignalStore.account.pniRegistrationId,
      recoveryPassword = null
    )

    val aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.aciPreKeys)
    val pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.pniIdentityKey, SignalStore.account.pniPreKeys)

    return accountManager.finishNewDeviceRegistration(
        verificationCode,
        accountAttributes,
        aciPreKeyCollection, pniPreKeyCollection,
        null
      )
  }
}
