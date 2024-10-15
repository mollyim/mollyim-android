package org.thoughtcrime.securesms.keyvalue

import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.model.UnifiedPushStatus

class UnifiedPushValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val MOLLYSOCKET_UUID = "mollysocket.uuid"
    private const val MOLLYSOCKET_DEVICE_ID = "mollysocket.deviceId"
    private const val MOLLYSOCKET_PASSWORD = "mollysocket.password"
    private const val MOLLYSOCKET_URL = "mollysocket.url"
    private const val MOLLYSOCKET_OK = "mollysocket.ok"
    private const val MOLLYSOCKET_FORBIDDEN_UUID = "mollysocket.forbidden_uuid"
    private const val MOLLYSOCKET_FORBIDDEN_ENDPOINT = "mollysocket.forbidden_endpoint"
    private const val MOLLYSOCKET_INTERNAL_ERROR = "mollysocket.internal_error"
    private const val UNIFIEDPUSH_ENDPOINT = "up.endpoint"
    private const val UNIFIEDPUSH_PENDING = "up.pending"
    private const val UNIFIEDPUSH_AIR_GAPPED = "up.air_gapped"
    private const val UNIFIEDPUSH_PINGED = "up.pinged"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup() = emptyList<String>()

  @get:JvmName("isEnabled")
  val enabled: Boolean = SignalStore.settings.preferredNotificationMethod == SettingsValues.NotificationDeliveryMethod.UNIFIEDPUSH

  var device: MollySocketDevice?
    get() {
      return MollySocketDevice(
        uuid = getString(MOLLYSOCKET_UUID, null) ?: return null,
        deviceId = getInteger(MOLLYSOCKET_DEVICE_ID, 0),
        password = getString(MOLLYSOCKET_PASSWORD, null) ?: return null,
      )
    }
    set(device) {
      store.beginWrite()
        .putString(MOLLYSOCKET_UUID, device?.uuid)
        .putInteger(MOLLYSOCKET_DEVICE_ID, device?.deviceId ?: 0)
        .putString(MOLLYSOCKET_PASSWORD, device?.password)
        .apply()
    }

  var endpoint: String? by stringValue(UNIFIEDPUSH_ENDPOINT, null)

  var pending: Boolean by booleanValue(UNIFIEDPUSH_PENDING, false)

  @get:JvmName("isAirGapped")
  var airGapped: Boolean by booleanValue(UNIFIEDPUSH_AIR_GAPPED, false)

  // This is set to true by default to avoid warning previous users,
  // It is set to false when registering a new device in
  // im.molly.unifiedpush.device.MollySocketLinkedDevice
  var pinged: Boolean by booleanValue(UNIFIEDPUSH_PINGED, true)

  var mollySocketUrl: String? by stringValue(MOLLYSOCKET_URL, null)

  var mollySocketFound: Boolean by booleanValue(MOLLYSOCKET_OK, false)

  var forbiddenUuid: Boolean by booleanValue(MOLLYSOCKET_FORBIDDEN_UUID, true)

  var forbiddenEndpoint: Boolean by booleanValue(MOLLYSOCKET_FORBIDDEN_ENDPOINT, true)

  var mollySocketInternalError: Boolean by booleanValue(MOLLYSOCKET_INTERNAL_ERROR, true)

  val status: UnifiedPushStatus
    get() = when {
      SignalStore.settings.preferredNotificationMethod != SettingsValues.NotificationDeliveryMethod.UNIFIEDPUSH -> UnifiedPushStatus.DISABLED
      SignalStore.unifiedpush.pending -> UnifiedPushStatus.PENDING
      SignalStore.unifiedpush.device == null -> UnifiedPushStatus.LINK_DEVICE_ERROR
      SignalStore.unifiedpush.endpoint == null -> UnifiedPushStatus.MISSING_ENDPOINT
      SignalStore.unifiedpush.airGapped &&
        !SignalStore.unifiedpush.pinged -> UnifiedPushStatus.AIR_GAPPED_NOT_PINGED
      SignalStore.unifiedpush.airGapped -> UnifiedPushStatus.AIR_GAPPED
      SignalStore.unifiedpush.mollySocketUrl.isNullOrBlank() ||
        !SignalStore.unifiedpush.mollySocketFound -> UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL
      SignalStore.unifiedpush.mollySocketInternalError -> UnifiedPushStatus.INTERNAL_ERROR
      SignalStore.unifiedpush.forbiddenUuid -> UnifiedPushStatus.FORBIDDEN_UUID
      SignalStore.unifiedpush.forbiddenEndpoint -> UnifiedPushStatus.FORBIDDEN_ENDPOINT
      !SignalStore.unifiedpush.pinged -> UnifiedPushStatus.NOT_PINGED
      else -> UnifiedPushStatus.OK
    }

  val isAvailableOrAirGapped: Boolean
    get() = status in listOf(UnifiedPushStatus.OK, UnifiedPushStatus.AIR_GAPPED)
}
