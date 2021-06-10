package org.thoughtcrime.securesms.components.settings.app.privacy

import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues

data class PrivacySettingsState(
  val blockedCount: Int,
  val blockUnknown: Boolean,
  val seeMyPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberSharingMode,
  val findMeByPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberListingMode,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val passphraseLock: Boolean,
  val passphraseLockTriggerValues: Set<String>,
  val passphraseLockTimeout: Long,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val universalExpireTimer: Int
)
