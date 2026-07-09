/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.PendingRestoreOption
import org.signal.registration.PreExistingRegistrationData
import kotlin.time.Duration

data class PhoneNumberEntryState(
  val regionCode: String = "",
  val countryCode: String = "",
  val countryName: String = "",
  val countryEmoji: String = "",
  val nationalNumber: String = "",
  val formattedNumber: String = "",
  val sessionE164: String? = null,
  val sessionMetadata: SessionMetadata? = null,
  val showSpinner: Boolean = false,
  val showDialog: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null,
  val preExistingRegistrationData: PreExistingRegistrationData? = null,
  val restoredSvrCredentials: List<NetworkController.SvrCredentials> = emptyList(),
  val pendingRestoreOption: PendingRestoreOption? = null,
  val initialized: Boolean = false,
  /** Whether the entered number has a plausible length for the selected country code. */
  val isNumberPossible: Boolean = false,
  /** Whether the entered number is definitively invalid. A still-too-short number is not considered invalid, since the user may simply be mid-entry. */
  val isNumberInvalid: Boolean = false
) {
  override fun toString(): String = "PhoneNumberEntryState(regionCode=$regionCode, countryCode=$countryCode, countryName=$countryName, countryEmoji=$countryEmoji, nationalNumber=$nationalNumber, formattedNumber=$formattedNumber, sessionE164=$sessionE164, sessionMetadata=${sessionMetadata?.let { "present" }}, showSpinner=$showSpinner, showDialog=$showDialog, oneTimeEvent=$oneTimeEvent, preExistingRegistrationData=${preExistingRegistrationData?.let { "present" }}, restoredSvrCredentials=${restoredSvrCredentials.size} items, pendingRestoreOption=$pendingRestoreOption, initialized=$initialized, isNumberPossible=$isNumberPossible, isNumberInvalid=$isNumberInvalid)"

  sealed interface OneTimeEvent {
    data object NetworkError : OneTimeEvent
    data object UnknownError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object UnableToSendSms : OneTimeEvent
    data object CouldNotRequestCodeWithSelectedTransport : OneTimeEvent
    data object InvalidPhoneNumber : OneTimeEvent
  }
}
