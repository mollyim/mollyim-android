package im.molly.unifiedpush.components.settings.app.notifications

import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.model.RegistrationStatus

data class Distributor(
  val applicationId: String,
  val name: String,
)

data class UnifiedPushSettingsState(
  val airGapped: Boolean,
  val device: MollySocketDevice?,
  val aci: String?,
  val registrationStatus: RegistrationStatus,
  val distributors: List<Distributor>,
  val selected: Int,
  val selectedNotAck: Boolean,
  val endpoint: String?,
  val mollySocketUrl: String?,
)
