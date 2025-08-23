package im.molly.unifiedpush

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import im.molly.unifiedpush.model.ConnectionRequest
import im.molly.unifiedpush.model.ConnectionResult
import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.model.Response
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.util.KeyHelper
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import java.io.IOException
import java.net.MalformedURLException

object MollySocketRepository {

  private val TAG = Log.tag(MollySocketRepository::class)

  private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

  private const val DEVICE_NAME = "MollySocket"

  @Throws(IOException::class, DeviceLimitExceededException::class)
  fun createDevice(): MollySocketDevice {
    Log.d(TAG, "Creating device for MollySocket")

    val password = Util.getSecret(18)
    val deviceId = verifyNewDevice(password)

    return MollySocketDevice(
      deviceId = deviceId,
      password = password,
    )
  }

  @Throws(IOException::class, DeviceLimitExceededException::class)
  private fun verifyNewDevice(password: String): Int {
    val verificationCode = when (val result = SignalNetwork.linkDevice.getDeviceVerificationCode()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> {
        Log.i(TAG, "Network failure", result.getCause())
        throw result.exception
      }
      is NetworkResult.StatusCodeError -> {
        Log.i(TAG, "Status code failure", result.getCause())
        throw result.exception
      }
    }

    val registrationId = KeyHelper.generateRegistrationId(false)
    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(
      DEVICE_NAME.toByteArray(), SignalStore.account.aciIdentityKey
    )

    val notDiscoverable = SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE

    val accountAttributes = AccountAttributes(
      signalingKey = null,
      registrationId = registrationId,
      fetchesMessages = true,
      registrationLock = null,
      unidentifiedAccessKey = null,
      unrestrictedUnidentifiedAccess = true,
      capabilities = AppCapabilities.getCapabilities(storageCapable = false),
      discoverableByPhoneNumber = !notDiscoverable,
      name = Base64.encodeWithPadding(encryptedDeviceName),
      pniRegistrationId = SignalStore.account.pniRegistrationId,
      recoveryPassword = null
    )

    val aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.aciPreKeys)
    val pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.pniIdentityKey, SignalStore.account.pniPreKeys)

    val accountManager = AccountManagerFactory.getInstance().createForDeviceLink(AppDependencies.application, password)

    return accountManager.finishNewDeviceRegistration(
      verificationCode.verificationCode,
      accountAttributes,
      aciPreKeyCollection, pniPreKeyCollection,
      null
    ).also {
      SignalStore.account.isMultiDevice = true
    }
  }

  // If loadDevices() fails, optimistically assume the device is linked
  fun MollySocketDevice.isLinked(): Boolean {
    return LinkDeviceRepository.loadDevices()?.any {
      it.id == deviceId && it.name == DEVICE_NAME
    } ?: true
  }

  fun discoverMollySocketServer(url: HttpUrl): Boolean {
    try {
      val request = Request.Builder().url(url).build()
      val client = AppDependencies.okHttpClient.newBuilder().build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.d(TAG, "Unexpected code: $response")
          return false
        }
        val body = response.body ?: run {
          Log.d(TAG, "No response body")
          return false
        }
        JsonUtils.fromJson(body.byteStream(), Response::class.java)
      }
      Log.d(TAG, "URL is OK")
    } catch (e: Exception) {
      Log.d(TAG, "Exception: $e")
      return when (e) {
        is MalformedURLException,
        is JsonParseException,
        is JsonMappingException,
        is JsonProcessingException -> false

        else -> throw IOException("Can not check server status")
      }
    }
    return true
  }

  @Throws(IOException::class)
  fun registerDeviceOnServer(
    url: HttpUrl,
    device: MollySocketDevice,
    endpoint: String,
    ping: Boolean = false,
  ): ConnectionResult? {
    val requestData = ConnectionRequest(
      uuid = SignalStore.account.requireAci().toString(),
      deviceId = device.deviceId,
      password = device.password,
      endpoint = endpoint,
      ping = ping,
    )

    val postBody = JsonUtils.toJson(requestData).toRequestBody(MEDIA_TYPE_JSON)
    val request = Request.Builder().url(url).post(postBody).build()
    val client = AppDependencies.okHttpClient.newBuilder().build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        Log.d(TAG, "Unexpected code: $response")
        return null
      }
      val body = response.body ?: run {
        Log.d(TAG, "No response body")
        return null
      }

      val resp = JsonUtils.fromJson(body.byteStream(), Response::class.java)

      val status = resp.mollySocket.status
      Log.d(TAG, "Status: $status")

      return status
    }
  }
}
