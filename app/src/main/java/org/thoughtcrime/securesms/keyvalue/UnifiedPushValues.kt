package org.thoughtcrime.securesms.keyvalue

import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.model.RegistrationStatus
import org.signal.core.util.logging.Log

class UnifiedPushValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(UnifiedPushValues::class)

    private const val MOLLYSOCKET_DEVICE_ID = "mollysocket.deviceId"
    private const val MOLLYSOCKET_PASSWORD = "mollysocket.passwd"
    private const val MOLLYSOCKET_STATUS = "mollysocket.status"
    private const val MOLLYSOCKET_AIR_GAPPED = "mollysocket.airGapped"
    private const val MOLLYSOCKET_URL = "mollysocket.url"
    private const val MOLLYSOCKET_VAPID = "mollysocket.vapid"
    private const val UNIFIEDPUSH_ENABLED = "up.enabled"
    private const val UNIFIEDPUSH_ENDPOINT = "up.endpoint"
    private const val UNIFIEDPUSH_LAST_RECEIVED_TIME = "up.lastRecvTime"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup() = emptyList<String>()

  @get:JvmName("isEnabled")
  var enabled: Boolean by booleanValue(UNIFIEDPUSH_ENABLED, false)

  var device: MollySocketDevice?
    get() {
      return MollySocketDevice(
        deviceId = getInteger(MOLLYSOCKET_DEVICE_ID, 0),
        password = getString(MOLLYSOCKET_PASSWORD, null) ?: return null,
      )
    }
    set(device) {
      store.beginWrite()
        .putInteger(MOLLYSOCKET_DEVICE_ID, device?.deviceId ?: 0)
        .putString(MOLLYSOCKET_PASSWORD, device?.password)
        .apply()
    }

  fun isMollySocketDevice(deviceId: Int): Boolean =
    deviceId != 0 && getInteger(MOLLYSOCKET_DEVICE_ID, 0) == deviceId

  var registrationStatus: RegistrationStatus
    get() = RegistrationStatus.fromValue(getInteger(MOLLYSOCKET_STATUS, -1)) ?: RegistrationStatus.UNKNOWN
    set(status) {
      putInteger(MOLLYSOCKET_STATUS, status.value)
    }

  var endpoint: String? by stringValue(UNIFIEDPUSH_ENDPOINT, null)

  var airGapped: Boolean by booleanValue(MOLLYSOCKET_AIR_GAPPED, false)

  var mollySocketUrl: String? by stringValue(MOLLYSOCKET_URL, null)

  var mollySocketVapid: String? by stringValue(MOLLYSOCKET_VAPID, null)

  var lastReceivedTime: Long by longValue(UNIFIEDPUSH_LAST_RECEIVED_TIME, 0)

  val isAvailableOrAirGapped: Boolean
    get() = enabled && registrationStatus == RegistrationStatus.REGISTERED
}
