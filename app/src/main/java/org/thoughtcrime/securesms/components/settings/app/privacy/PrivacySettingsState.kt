package org.thoughtcrime.securesms.components.settings.app.privacy

data class PrivacySettingsState(
  val blockedCount: Int,
  val blockUnknown: Boolean,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val passphraseLock: Boolean,
  val passphraseLockTriggerValues: Set<String>,
  val passphraseLockTimeout: Long,
  val biometricScreenLock: Boolean,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val universalExpireTimer: Int,
  val hasDuressCode: Boolean,
)
