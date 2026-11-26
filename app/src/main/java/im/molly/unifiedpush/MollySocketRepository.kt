package im.molly.unifiedpush

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import im.molly.unifiedpush.model.ConnectionRequest
import im.molly.unifiedpush.model.ConnectionResult
import im.molly.unifiedpush.model.LinkStatus
import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.model.Response
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.signal.core.util.Base64
import org.signal.core.util.JsonUtils
import org.signal.core.util.Util
import org.signal.core.util.crypto.DeviceNameCipher
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.network.api.RegistrationApiV2
import org.signal.network.api.RegistrationApiV2.DeviceAttributes
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.AppDependencies.keysApi
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.whispersystems.signalservice.api.NetworkResultUtil
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.DeviceLimit
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import java.io.IOException
import java.net.MalformedURLException

object MollySocketRepository {

  private val TAG = Log.tag(MollySocketRepository::class)

  private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

  private const val DEVICE_NAME = "MollySocket"

  @Throws(IOException::class, DeviceLimitExceededException::class)
  suspend fun createDevice(): MollySocketDevice {
    Log.d(TAG, "Creating device for MollySocket")

    val password = Util.getSecret(18)
    val deviceId = verifyNewDevice(password)

    return MollySocketDevice(
      deviceId = deviceId,
      password = password,
    )
  }

  @Throws(IOException::class, DeviceLimitExceededException::class)
  private suspend fun verifyNewDevice(password: String): Int {
    val fetchResult = AppDependencies.linkDeviceApi.getDeviceVerificationCode()
    val verificationCode = fetchResult.successOrThrow()

    val registrationId = KeyHelper.generateRegistrationId(false)
    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(
      DEVICE_NAME.toByteArray(), SignalStore.account.aciIdentityKey
    )

    // The server checks the optionalPhoneNumber capability with numberless accounts
    // https://github.com/signalapp/Signal-Server/blob/398888f224289f2e52baa78e2df285ae179eaad0/service/src/main/java/org/whispersystems/textsecuregcm/controllers/DeviceController.java#L247
    val deviceAttributes = DeviceAttributes(
      fetchesMessages = true,
      registrationId = registrationId,
      pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
      name = Base64.encodeWithPadding(encryptedDeviceName),
      capabilities = RegistrationApiV2.AccountAttributes.Capabilities(
        storage = false,
        versionedExpirationTimer = true,
        attachmentBackfill = true,
        spqr = true,
        usernameChangeSyncMessage = true,
        optionalPhoneNumber = true
      )
    )

    val aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.aciPreKeys).let {
      RegistrationApiV2.PreKeyCollection(
        it.identityKey,
        it.signedPreKey,
        it.lastResortKyberPreKey
      )
    }

    val pniPreKeyCollection = SignalStore.account.pniIdentityKeyOrNull?.let { pniKey ->
      RegistrationRepository.generateSignedAndLastResortPreKeys(pniKey, SignalStore.account.pniPreKeys).let {
        RegistrationApiV2.PreKeyCollection(
          it.identityKey,
          it.signedPreKey,
          it.lastResortKyberPreKey
        )
      }
    }

    val result = AppDependencies.registrationApiV2.registerAsSecondaryDevice(
      SignalStore.account.aci!!,
      password,
      verificationCode.verificationCode,
      deviceAttributes,
      aciPreKeyCollection,
      pniPreKeyCollection,
      null
    )

    when (result) {
      is RequestResult.Success -> {
        val deviceId = result.result.deviceId
        SignalStore.account.isMultiDevice = true
        loadPreKeys(deviceId)
        return deviceId
      }
      is RequestResult.NonSuccess<RegistrationApiV2.RegisterAsLinkedDeviceError> -> {
        if (result.error is RegistrationApiV2.RegisterAsLinkedDeviceError.MaxLinkedDevices) {
          throw DeviceLimitExceededException(DeviceLimit())
        } else {
          throw IOException("NonSuccess linked device response: ${result.error}")
        }
      }
      else -> {
        throw IOException("Unexpected error: $result")
      }
    }
  }

  /**
   * We need to load prekeys, else we get a bug if MollySocket is the only linked device:
   * - SignalStore.account.isMultiDevice is set to `true` => we try to [sendSyncMessage][org.whispersystems.signalservice.api.SignalServiceMessageSender.sendSyncMessage]
   * - [SignalServiceAccountDataStore.containsSession] returns false for this device
   * - So, [getEncryptedMessage][org.whispersystems.signalservice.api.SignalServiceMessageSender.getEncryptedMessage] removes this device => it returns a OutgoingPushMessageList with an empty message list
   * - [sendMessage][org.whispersystems.signalservice.api.SignalServiceMessageSender.sendMessage] fails with NonSuccessfulResponseCodeException: [400],
   *     we don't get the Mismatched device exception [409] which allow handling ExtraDevices/MissingDevices.
   * - We're stuck in a loop where we can't send a message.
   */
  private fun loadPreKeys(deviceId: Int) {
    val recipient = SignalServiceAddress(Recipient.self().requireAci())
    val localProtocolAddress = SignalProtocolAddress(recipient.identifier, deviceId)
    val aciStore = AppDependencies.protocolStore.aci()
    val preKeys = NetworkResultUtil.toPreKeysLegacy(
      keysApi.getPreKeysSync(
        destination = recipient, sealedSenderAccess = null, deviceId = deviceId
      )
    )
    preKeys.forEach { preKey ->
      val preKeyAddress = SignalProtocolAddress(recipient.identifier, preKey.deviceId)
      val sessionBuilder = SignalSessionBuilder(
        ReentrantSessionLock.INSTANCE,
        SessionBuilder(aciStore, preKeyAddress, localProtocolAddress)
      )
      sessionBuilder.process(preKey)
    }
  }

  fun removeDevice(device: MollySocketDevice): Boolean {
    return runBlocking {
      LinkDeviceRepository.removeDevice(device.deviceId)
    }
  }

  fun getDeviceStatus(device: MollySocketDevice): LinkStatus {
    return when (device.isLinked()) {
      true -> LinkStatus.LINKED
      false -> LinkStatus.NOT_LINKED
      else -> LinkStatus.UNKNOWN
    }
  }

  private fun MollySocketDevice.isLinked(): Boolean? {
    return runBlocking {
      LinkDeviceRepository.loadDevices()?.any {
        it.id == deviceId && it.name == DEVICE_NAME
      }
    }
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
        val body = response.body
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
      val body = response.body
      val resp = JsonUtils.fromJson(body.byteStream(), Response::class.java)

      val status = resp.mollySocket.status
      Log.d(TAG, "Status: $status")

      return status
    }
  }
}
