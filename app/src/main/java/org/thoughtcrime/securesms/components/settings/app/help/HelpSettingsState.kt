package org.thoughtcrime.securesms.components.settings.app.help

data class HelpSettingsState(
  val updateApkEnabled: Boolean,
  val includeBetaEnabled: Boolean,
  val logEnabled: Boolean,
  val lastUpdateCheckTime: Long
)
