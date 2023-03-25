package im.molly.unifiedpush.model

data class MollySocketDevice(
  val deviceId: Int,
  val password: String,
) {
  override fun toString(): String {
    return "MollySocketDevice:$deviceId"
  }
}

enum class RegistrationStatus(val value: Int, val notifyUser: Boolean = false) {
  UNKNOWN(0),
  PENDING(1),
  REGISTERED(2),
  BAD_RESPONSE(3),
  SERVER_ERROR(4),
  FORBIDDEN_UUID(5, notifyUser = true),
  FORBIDDEN_ENDPOINT(6, notifyUser = true);

  companion object {
    fun fromValue(value: Int): RegistrationStatus? {
      return entries.firstOrNull { it.value == value }
    }
  }
}

fun ConnectionResult?.toRegistrationStatus():RegistrationStatus = when (this) {
  ConnectionResult.OK -> RegistrationStatus.REGISTERED
  ConnectionResult.INTERNAL_ERROR,
  ConnectionResult.FORBIDDEN -> RegistrationStatus.SERVER_ERROR
  ConnectionResult.INVALID_UUID -> RegistrationStatus.FORBIDDEN_UUID
  ConnectionResult.INVALID_ENDPOINT -> RegistrationStatus.FORBIDDEN_ENDPOINT
  null -> RegistrationStatus.BAD_RESPONSE
}
