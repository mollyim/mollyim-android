/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import kotlin.time.Duration

/** The native Molly passphrase operation requested by the privacy screen. */
enum class PassphraseDialogMode {
  ENABLE,
  DISABLE,
  CHANGE
}

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the screen
 * itself. Navigation doesn't come through here: the view model sends it straight to its parent.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PrivacySettingsAction {

  /** Open the native Molly dialog without changing a setting until it succeeds. */
  data class LaunchPassphraseDialog(val mode: PassphraseDialogMode) : PrivacySettingsAction

  /** Open Molly's original timeout picker, which supports seconds and zero for instant locking. */
  data class LaunchPassphraseLockTimeoutPicker(val timeout: Duration) : PrivacySettingsAction

  /** Check biometric availability, enroll if necessary, and authenticate before enabling the lock. */
  data object EnableBiometricScreenLock : PrivacySettingsAction

  /** Apply the newly persisted biometric lock state to recents and window security. */
  data class ApplyBiometricScreenLock(val enabled: Boolean) : PrivacySettingsAction

  /** Open the support article explaining incognito keyboard. */
  data object OpenIncognitoKeyboardLearnMore : PrivacySettingsAction

  /** Apply the screen security setting to the current window. */
  data class ApplyScreenSecurity(val enabled: Boolean) : PrivacySettingsAction
}
