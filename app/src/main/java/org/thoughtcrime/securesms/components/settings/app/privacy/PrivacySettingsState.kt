package org.thoughtcrime.securesms.components.settings.app.privacy

import org.thoughtcrime.securesms.screenlock.ScreenLockMode

data class PrivacySettingsState(
  val blockedCount: Int,
  val blockUnknown: Boolean,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val passphraseLock: Boolean,
  val passphraseLockTriggerValues: Set<String>,
  val passphraseLockTimeout: Long,
  val screenLockMode: ScreenLockMode,
  val screenLockTimeout: Long,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val universalExpireTimer: Int
)
