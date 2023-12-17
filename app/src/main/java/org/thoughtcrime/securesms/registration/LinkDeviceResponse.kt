package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.account.PreKeyCollection

data class LinkDeviceResponse(
  val deviceName: String?,
  val provisionData: SignalServiceAccountManager.ProvisionDecryptResult,
  val deviceId: Int,
  val aciPreKeys: PreKeyCollection,
  val pniPreKeys: PreKeyCollection
)
