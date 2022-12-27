package org.thoughtcrime.securesms.keyvalue

import im.molly.unifiedpush.model.MollyDevice
import im.molly.unifiedpush.model.UnifiedPushStatus

internal class UnifiedPushValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val MOLLYSOCKET_UUID = "unifiedpush.mollysocket.uuid"
    private const val MOLLYSOCKET_DEVICE_ID = "unifiedpush.mollysocket.deviceId"
    private const val MOLLYSOCKET_PASSWORD = "unifiedpush.mollysocket.password"
    private const val MOLLYSOCKET_URL = "unifiedpush.mollysocket.url"
    private const val MOLLYSOCKET_OK = "unifiedpush.mollysocket.ok"
    private const val MOLLYSOCKET_FORBIDDEN_UUID = "unifiedpush.mollysocket.forbidden_uuid"
    private const val MOLLYSOCKET_FORBIDDEN_ENDPOINT = "unifiedpush.mollysocket.forbidden_endpoint"
    private const val MOLLYSOCKET_INTERNAL_ERROR = "unifiedpush.mollysocket.internal_error"
    private const val UNIFIEDPUSH_ENDPOINT = "unifiedpush.endpoint"
    private const val UNIFIEDPUSH_ENABLED = "unifiedpush.enabled"
    private const val UNIFIEDPUSH_AIR_GAPED = "unifiedpush.air_gaped"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup() = emptyList<String>()

  var device: MollyDevice?
    get() {
      return MollyDevice(
        uuid = getString(MOLLYSOCKET_UUID, null) ?: return null,
        deviceId = getInteger(MOLLYSOCKET_DEVICE_ID, 0),
        password = getString(MOLLYSOCKET_PASSWORD, null) ?: return null,
      )
    }
    set(device) {
      if (device == null) {
        remove(MOLLYSOCKET_UUID)
        remove(MOLLYSOCKET_DEVICE_ID)
        remove(MOLLYSOCKET_PASSWORD)
      } else {
        putString(MOLLYSOCKET_UUID, device.uuid)
        putInteger(MOLLYSOCKET_DEVICE_ID, device.deviceId)
        putString(MOLLYSOCKET_PASSWORD, device.password)
      }
    }

  var endpoint: String? by stringValue(UNIFIEDPUSH_ENDPOINT, null)

  var enabled: Boolean by booleanValue(UNIFIEDPUSH_ENABLED, false)

  var airGaped: Boolean by booleanValue(UNIFIEDPUSH_AIR_GAPED, false)

  var mollySocketUrl: String? by stringValue(MOLLYSOCKET_URL, null)

  var mollySocketFound: Boolean by booleanValue(MOLLYSOCKET_OK, false)

  var forbiddenUuid: Boolean by booleanValue(MOLLYSOCKET_FORBIDDEN_UUID, true)

  var forbiddenEndpoint: Boolean by booleanValue(MOLLYSOCKET_FORBIDDEN_ENDPOINT, true)

  var mollySocketInternalError: Boolean by booleanValue(MOLLYSOCKET_INTERNAL_ERROR, true)

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
