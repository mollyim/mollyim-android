package im.molly.unifiedpush.util

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import im.molly.unifiedpush.model.RegistrationStatus
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.JsonUtils
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

data class Response(
  @JsonProperty("mollysocket") val mollySocket: ResponseMollySocket,
)

data class ResponseMollySocket(
  @JsonProperty("version") val version: String,
  @JsonProperty("status") val status: RegistrationStatus?,
)

data class ConnectionData(
  @JsonProperty("uuid") val uuid: String,
  @JsonProperty("device_id") val device_id: Int,
  @JsonProperty("password") val password: String,
  @JsonProperty("endpoint") val endpoint: String,
)

object MollySocketRequest {
  private val TAG = Log.tag(MollySocketRequest::class.java)
  private val JsonMediaType = MediaType.parse("application/json; charset=utf-8")

  fun discoverMollySocketServer(): Boolean {
    try {
      val url = URL(SignalStore.unifiedpush().mollySocketUrl)
      val request = Request.Builder().url(url).build()
      val client = ApplicationDependencies.getOkHttpClient().newBuilder().build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.d(TAG, "Unexpected code $response")
          return false
        }
        val body = response.body() ?: run {
          Log.d(TAG, "Response body was not present")
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

  fun registerToMollySocketServer(): RegistrationStatus {
    try {
      val data = SignalStore.unifiedpush().device?.let {
        val endpoint = SignalStore.unifiedpush().endpoint ?: return RegistrationStatus.NO_ENDPOINT
        ConnectionData(
          uuid = it.uuid,
          device_id = it.deviceId,
          password = it.password,
          endpoint = endpoint
        )
      } ?: return RegistrationStatus.NO_DEVICE

      val url = URL(SignalStore.unifiedpush().mollySocketUrl)
      val postBody = RequestBody.create(JsonMediaType, JsonUtils.toJson(data))
      val request = Request.Builder().url(url).post(postBody).build()
      val client = ApplicationDependencies.getOkHttpClient().newBuilder().build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.d(TAG, "Unexpected code $response")
          return RegistrationStatus.INTERNAL_ERROR
        }
        val body = response.body() ?: run {
          Log.d(TAG, "Response body was not present")
          return RegistrationStatus.INTERNAL_ERROR
        }
        val resp = JsonUtils.fromJson(body.byteStream(), Response::class.java)
        Log.d(TAG, "Status: ${resp.mollySocket.status}")
        return resp.mollySocket.status ?: RegistrationStatus.INTERNAL_ERROR
      }
    } catch (e: Exception) {
      Log.d(TAG, "Exception: $e")
      return RegistrationStatus.INTERNAL_ERROR
    }
  }
}
