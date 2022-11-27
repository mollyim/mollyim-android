package im.molly.unifiedpush.components.settings.app.notifications

import im.molly.unifiedpush.model.MollyDevice

data class Distributor(
  val applicationId: String,
  val name: String,
)

enum class UnifiedPushStatus {
  DISABLED,
  AIR_GAPED,
  SERVER_NOT_FOUND_AT_URL,
  MISSING_ENDPOINT,
  NO_DISTRIBUTOR,
  PENDING,
  OK,
  INTERNAL_ERROR,
  UNKNOWN,
}

data class UnifiedPushSettingsState(
  val enabled: Boolean,
  val airGaped: Boolean,
  val device: MollyDevice?,
  val distributors: List<Distributor>,
  val selected: Int,
  val endpoint: String?,
  val mollySocketUrl: String?,
  val mollySocketOk: Boolean?,
  var status: UnifiedPushStatus,
)

fun UnifiedPushSettingsState.setStatus() {
  if (!this.enabled) {
    this.status = UnifiedPushStatus.DISABLED
  }
  if (this.airGaped) {
    this.status = UnifiedPushStatus.AIR_GAPED
  }
  if (this.mollySocketUrl.isNullOrBlank()) {
    this.status =  UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL
  }
  this.status =  when (this.mollySocketOk) {
    null -> UnifiedPushStatus.PENDING
    true -> UnifiedPushStatus.OK
    false -> UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL
  }
}
