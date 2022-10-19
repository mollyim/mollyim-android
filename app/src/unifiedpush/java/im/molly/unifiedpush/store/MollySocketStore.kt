package im.molly.unifiedpush.store

import android.content.Context
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.UUID

class MollySocketStore {
  private val PREF_MASTER = "unifiedpush"
  private val PREF_MASTER_SOCKET_URI = "unifiedpush.socketUri"
  private val PREF_MASTER_DEVICE_ID = "unifiedpush.deviceId"

  private val context = ApplicationDependencies.getApplication()
  private val prefs = context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)

  fun getUri(): String? {
    return prefs.getString(PREF_MASTER_SOCKET_URI, null)
  }

  private fun saveUri(uri: String) {
    prefs.edit()?.putString(PREF_MASTER_SOCKET_URI, uri)?.apply()
  }

  fun saveUri(uuid: UUID, deviceId: Int, password: String) {
    val wsUriFormat = ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration()
      .signalServiceUrls[0].url
      .replace("https://", "wss://")
      .replace("http://", "ws://") + "/v1/websocket/?login=%s.%s&password=%s"

    saveUri(
      String.format(wsUriFormat, uuid, deviceId, password)
    )
  }

  fun removeUri() {
    prefs.edit().remove(PREF_MASTER_SOCKET_URI).apply()
  }

  fun getDeviceId(): Int {
    return prefs.getInt(PREF_MASTER_DEVICE_ID, SignalServiceAddress.DEFAULT_DEVICE_ID)
  }

  fun saveDeviceId(deviceId: Int) {
    prefs.edit().putInt(PREF_MASTER_DEVICE_ID, deviceId).apply()
  }

  fun removeDeviceId() {
    prefs.edit().remove(PREF_MASTER_DEVICE_ID).apply()
  }
}