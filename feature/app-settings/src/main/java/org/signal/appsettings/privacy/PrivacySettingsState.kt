/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import kotlin.time.Duration

/** A selectable automatic-lockdown trigger, with its localized display label. */
data class PassphraseLockTriggerOption(
  val key: String,
  val label: String
)

/**
 * Everything [PrivacySettingsScreen] needs to render.
 */
data class PrivacySettingsState(
  val hasPhoneNumber: Boolean = true,
  val blockedCount: Int = 0,
  val blockUnknown: Boolean = false,
  val readReceipts: Boolean = false,
  val typingIndicators: Boolean = false,
  val universalExpireTimer: Duration = Duration.ZERO,
  val passphraseLock: Boolean = false,
  val passphraseLockTriggerValues: Set<String> = emptySet(),
  val passphraseLockTriggerOptions: List<PassphraseLockTriggerOption> = emptyList(),
  val isPassphraseLockTimeoutEnabled: Boolean = false,
  val passphraseLockTimeout: Duration = Duration.ZERO,
  val biometricScreenLock: Boolean = false,
  val screenSecurity: Boolean = false,
  val isScreenSecurityForced: Boolean = false,
  val incognitoKeyboard: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Biometric auto-lock can force secure windows independently of the user's screen-security preference. */
  val isScreenSecurityEnabled: Boolean
    get() = screenSecurity || isScreenSecurityForced

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Lets the user choose when Molly should automatically require its passphrase. */
    data object AutomaticLockdown : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** The device has no biometric hardware, or it is currently unavailable. */
    data object BiometricsUnavailable : Snackbar

    /** Biometrics are supported but none are enrolled. */
    data object BiometricEnrollmentRequired : Snackbar

    /** The system settings for setting up device security couldn't be opened. */
    data object DeviceSecuritySettingsUnavailable : Snackbar
  }
}
