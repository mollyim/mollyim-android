/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.core.util.censor
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult

sealed class PhoneNumberEntryScreenEvents {
  data class CountryCodeChanged(val value: String) : PhoneNumberEntryScreenEvents()
  data class PhoneNumberChanged(val value: String) : PhoneNumberEntryScreenEvents()
  data class CountrySelected(val countryCode: Int, val regionCode: String, val countryName: String, val countryEmoji: String) : PhoneNumberEntryScreenEvents()
  data object PhoneNumberEntered : PhoneNumberEntryScreenEvents()
  data object PhoneNumberCancelled : PhoneNumberEntryScreenEvents()
  data object PhoneNumberSubmitted : PhoneNumberEntryScreenEvents()
  data object CountryPicker : PhoneNumberEntryScreenEvents()
  data class CaptchaCompleted(val token: String) : PhoneNumberEntryScreenEvents() {
    override fun toString(): String = "CaptchaCompleted(token=${token.censor()})"
  }

  /** The pre-registration local backup restore flow returned a result. */
  data class LocalBackupRestoreCompleted(val result: LocalBackupRestoreResult) : PhoneNumberEntryScreenEvents() {
    override fun toString(): String = "LocalBackupRestoreCompleted(result=***)"
  }
  data object ConsumeOneTimeEvent : PhoneNumberEntryScreenEvents()
}
