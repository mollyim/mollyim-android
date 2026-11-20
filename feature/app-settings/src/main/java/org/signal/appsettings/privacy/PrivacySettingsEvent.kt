/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import kotlin.time.Duration

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface PrivacySettingsEvent {

  /** The screen came back to the foreground, so the blocked count and device security may have changed. */
  data object ScreenResumed : PrivacySettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : PrivacySettingsEvent

  /** The user tapped the phone number row. */
  data object PhoneNumberClicked : PrivacySettingsEvent

  /** The user tapped the blocked row. */
  data object BlockedClicked : PrivacySettingsEvent

  /** The user flipped the block-unknown-contacts switch. */
  data class BlockUnknownToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user flipped the read receipts switch. */
  data class ReadReceiptsToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user flipped the typing indicators switch. */
  data class TypingIndicatorsToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped the default disappearing messages timer row. */
  data object DefaultTimerClicked : PrivacySettingsEvent

  /** Requests the native passphrase dialog. No setting changes until the dialog reports success. */
  data class PassphraseLockToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped the change passphrase row. */
  data object ChangePassphraseClicked : PrivacySettingsEvent

  /** The user tapped the automatic-lockdown row. */
  data object AutomaticLockdownClicked : PrivacySettingsEvent

  /** The user confirmed an automatic-lockdown selection. */
  data class AutomaticLockdownSelected(val triggers: Set<String>) : PrivacySettingsEvent

  /** The user tapped the inactivity timeout row. */
  data object PassphraseLockTimeoutClicked : PrivacySettingsEvent

  /** The user picked an inactivity timeout. */
  data class PassphraseLockTimeoutSelected(val timeout: Duration) : PrivacySettingsEvent

  /** The user requested a biometric lock change. Enabling requires authentication first. */
  data class BiometricScreenLockToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** Sent by the host after successful biometric authentication. */
  data object BiometricScreenLockEnableAuthenticated : PrivacySettingsEvent

  /** Biometric hardware is absent or currently unavailable. */
  data object BiometricScreenLockUnavailable : PrivacySettingsEvent

  /** The device supports biometrics but the user has not enrolled any. */
  data object BiometricEnrollmentRequired : PrivacySettingsEvent

  /** The user flipped the screen security switch. */
  data class ScreenSecurityToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user flipped the incognito keyboard switch. */
  data class IncognitoKeyboardToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped "Learn more" under the incognito keyboard switch. */
  data object IncognitoKeyboardLearnMoreClicked : PrivacySettingsEvent

  /** The host couldn't open any of the system settings screens for setting up device security. */
  data object DeviceSecuritySettingsUnavailable : PrivacySettingsEvent

  /** The user tapped the advanced row. */
  data object AdvancedClicked : PrivacySettingsEvent

  /** Dismisses whatever is in [PrivacySettingsState.dialog]. */
  data object DialogDismissed : PrivacySettingsEvent

  /** The snackbar in [PrivacySettingsState.snackbar] has gone away. */
  data object SnackbarDismissed : PrivacySettingsEvent
}
