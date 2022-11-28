package im.molly.unifiedpush.model

import org.thoughtcrime.securesms.keyvalue.SignalStore

enum class RegistrationStatus(private val formatted: String) {
  OK("ok"),
  FORBIDDEN("forbidden"),
  INVALID_UUID("invalid_uuid"),
  INTERNAL_ERROR("internal_error"),
  NO_DEVICE("_1"),
  NO_ENDPOINT("_2");

  override fun toString(): String {
    return formatted
  }
}

fun RegistrationStatus.saveStatus() {
  when (this) {
    RegistrationStatus.OK -> {
      SignalStore.unifiedpush().forbiddenUuid = false
      SignalStore.unifiedpush().mollySocketInternalError = false
      SignalStore.unifiedpush().forbiddenEndpoint = false
    }
    RegistrationStatus.INVALID_UUID -> {
      SignalStore.unifiedpush().forbiddenUuid = true
      SignalStore.unifiedpush().mollySocketInternalError = false
      SignalStore.unifiedpush().forbiddenEndpoint = false
    }
    // We tried to register without device
    RegistrationStatus.NO_DEVICE,
      // Should never be called: that means the linked device is deleted
    RegistrationStatus.FORBIDDEN,
    RegistrationStatus.NO_ENDPOINT,
    RegistrationStatus.INTERNAL_ERROR -> {
      SignalStore.unifiedpush().mollySocketInternalError = true
    }
    //TODO: RegistrationStatus.FORBIDDEN_ENDPOINT
  }
}