package im.molly.unifiedpush.store

import android.content.Context
import im.molly.unifiedpush.model.MollyDevice
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

class UnifiedPushValues {
  private val PREF_MASTER = "unifiedpush.mollysocket"
  private val MOLLYSOCKET_UUID = "unifiedpush.mollysocket.uuid"
  private val MOLLYSOCKET_DEVICE_ID = "unifiedpush.mollysocket.deviceId"
  private val MOLLYSOCKET_PASSWORD = "unifiedpush.mollysocket.password"
  private val MOLLYSOCKET_URL = "unifiedpush.mollysocket.url"
  private val MOLLYSOCKET_OK = "unifiedpush.mollysocket.ok"
  private val MOLLYSOCKET_FORBIDDEN_UUID = "unifiedpush.mollysocket.forbidden_uuid"
  private val MOLLYSOCKET_FORBIDDEN_ENDPOINT = "unifiedpush.mollysocket.forbidden_endpoint"
  private val MOLLYSOCKET_INTERNAL_ERROR = "unifiedpush.mollysocket.internal_error"
  private val UNIFIEDPUSH_ENDPOINT = "unifiedpush.endpoint"
  private val UNIFIEDPUSH_ENABLED = "unifiedpush.enabled"
  private val UNIFIEDPUSH_AIR_GAPED = "unifiedpush.air_gaped"

  private val context = ApplicationDependencies.getApplication()
  private val prefs = context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)

  var device: MollyDevice?
    get() {
      return MollyDevice(
        uuid = prefs.getString(MOLLYSOCKET_UUID, null) ?: return null,
        deviceId = prefs.getInt(MOLLYSOCKET_DEVICE_ID, 0),
        password = prefs.getString(MOLLYSOCKET_PASSWORD, null) ?: return null,
      )
    }
  set(device) {
    if (device == null) {
      prefs.edit().remove(MOLLYSOCKET_UUID).apply()
      prefs.edit().remove(MOLLYSOCKET_DEVICE_ID).apply()
      prefs.edit().remove(MOLLYSOCKET_PASSWORD).apply()
    } else {
      prefs.edit().putString(MOLLYSOCKET_UUID, device.uuid).apply()
      prefs.edit().putInt(MOLLYSOCKET_DEVICE_ID, device.deviceId).apply()
      prefs.edit().putString(MOLLYSOCKET_PASSWORD, device.password).apply()
    }
  }

  var endpoint: String?
  get() = prefs.getString(UNIFIEDPUSH_ENDPOINT, null)
  set(value) = if (value == null) {
      prefs.edit().remove(UNIFIEDPUSH_ENDPOINT).apply()
    } else {
      prefs.edit().putString(UNIFIEDPUSH_ENDPOINT, value).apply()
    }

  var enabled: Boolean
    get() = prefs.getBoolean(UNIFIEDPUSH_ENABLED, false)
    set(value) = prefs.edit().putBoolean(UNIFIEDPUSH_ENABLED, value).apply()

  var airGaped: Boolean
    get() = prefs.getBoolean(UNIFIEDPUSH_AIR_GAPED, false)
    set(value) = prefs.edit().putBoolean(UNIFIEDPUSH_AIR_GAPED, value).apply()

  var mollySocketUrl: String?
    get() = prefs.getString(MOLLYSOCKET_URL, null)
    set(value) = if (value == null) {
        prefs.edit().remove(MOLLYSOCKET_URL).apply()
      } else {
        prefs.edit().putString(MOLLYSOCKET_URL, value).apply()
      }

  var mollySocketFound: Boolean
    get() = prefs.getBoolean(MOLLYSOCKET_OK, false)
    set(value) = prefs.edit().putBoolean(MOLLYSOCKET_OK, value).apply()

  var forbiddenUuid: Boolean
    get() = prefs.getBoolean(MOLLYSOCKET_FORBIDDEN_UUID, true)
    set(value) = prefs.edit().putBoolean(MOLLYSOCKET_FORBIDDEN_UUID, value).apply()

  var forbiddenEndpoint: Boolean
    get() = prefs.getBoolean(MOLLYSOCKET_FORBIDDEN_ENDPOINT, true)
    set(value) = prefs.edit().putBoolean(MOLLYSOCKET_FORBIDDEN_ENDPOINT, value).apply()

  var mollySocketInternalError: Boolean
    get() = prefs.getBoolean(MOLLYSOCKET_INTERNAL_ERROR, true)
    set(value) = prefs.edit().putBoolean(MOLLYSOCKET_INTERNAL_ERROR, value).apply()

  val status: UnifiedPushStatus
    get() = when {
        !SignalStore.unifiedpush().enabled -> UnifiedPushStatus.DISABLED
        SignalStore.unifiedpush().device == null -> UnifiedPushStatus.LINK_DEVICE_ERROR
        SignalStore.unifiedpush().endpoint == null -> UnifiedPushStatus.MISSING_ENDPOINT
        SignalStore.unifiedpush().airGaped -> UnifiedPushStatus.AIR_GAPED
        SignalStore.unifiedpush().mollySocketUrl.isNullOrBlank() ||
          !SignalStore.unifiedpush().mollySocketFound -> UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL
        SignalStore.unifiedpush().forbiddenUuid -> UnifiedPushStatus.FORBIDDEN_UUID
        SignalStore.unifiedpush().forbiddenEndpoint -> UnifiedPushStatus.FORBIDDEN_ENDPOINT
        else -> UnifiedPushStatus.OK
      }
}