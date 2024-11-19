package im.molly.unifiedpush.model

data class MollySocketDevice(
  val deviceId: Int,
  val password: String,
) {
  override fun toString(): String {
    return "MollySocketDevice:$deviceId"
  }
}

enum class RegistrationStatus(val value: Int) {
  UNKNOWN(0),
  PENDING(1),
  REGISTERED(2),
  BAD_RESPONSE(3),
  SERVER_ERROR(4),
  /** The UUID is forbidden by the config of MollySocket */
  FORBIDDEN_UUID(5),
  /** The endpoint is forbidden by the config of MollySocket */
  FORBIDDEN_ENDPOINT(6),
  /** The account+password doesn't work anymore, and returns forbidden by Signal server */
  FORBIDDEN_PASSWORD(7);

  companion object {
    fun fromValue(value: Int): RegistrationStatus? {
      return entries.firstOrNull { it.value == value }
    }
  }
}

fun ConnectionResult?.toRegistrationStatus():RegistrationStatus = when (this) {
  ConnectionResult.OK -> RegistrationStatus.REGISTERED
  ConnectionResult.INTERNAL_ERROR -> RegistrationStatus.SERVER_ERROR
  ConnectionResult.FORBIDDEN -> RegistrationStatus.FORBIDDEN_PASSWORD
  ConnectionResult.INVALID_UUID -> RegistrationStatus.FORBIDDEN_UUID
  ConnectionResult.INVALID_ENDPOINT -> RegistrationStatus.FORBIDDEN_ENDPOINT
  null -> RegistrationStatus.BAD_RESPONSE
}
