package org.thoughtcrime.securesms.components.settings.app.data

import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.webrtc.CallDataMode

data class DataAndStorageSettingsState(
  val totalStorageUse: Long,
  val mobileAutoDownloadValues: Set<String>,
  val wifiAutoDownloadValues: Set<String>,
  val roamingAutoDownloadValues: Set<String>,
  val callDataMode: CallDataMode,
  val sentMediaQuality: SentMediaQuality,
)
