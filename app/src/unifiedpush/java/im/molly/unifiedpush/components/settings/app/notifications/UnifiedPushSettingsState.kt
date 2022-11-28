package im.molly.unifiedpush.components.settings.app.notifications

import im.molly.unifiedpush.model.MollyDevice
import im.molly.unifiedpush.model.UnifiedPushStatus

data class Distributor(
  val applicationId: String,
  val name: String,
)

data class UnifiedPushSettingsState(
  val enabled: Boolean,
  val airGaped: Boolean,
  val device: MollyDevice?,
  val distributors: List<Distributor>,
  val selected: Int,
  val endpoint: String?,
  val mollySocketUrl: String?,
  val mollySocketOk: Boolean,
  var status: UnifiedPushStatus,
)
