/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PrivacySettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<PrivacySettingsEvent>()

  /** Everything but the [PrivacySettingsEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<PrivacySettingsEvent>
    get() = events.filterNot { it == PrivacySettingsEvent.ScreenResumed }

  @Test
  fun `resuming the screen emits ScreenResumed and nothing else`() {
    setContent(PrivacySettingsState())

    assertThat(events).containsExactly(PrivacySettingsEvent.ScreenResumed)
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(PrivacySettingsState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `phone number row is shown only with a phone number`() {
    setContent(PrivacySettingsState(hasPhoneNumber = false))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER).assertDoesNotExist()
  }

  @Test
  fun `clicking the phone number row emits PhoneNumberClicked`() {
    setContent(PrivacySettingsState(hasPhoneNumber = true))

    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.PhoneNumberClicked)
  }

  @Test
  fun `the blocked row shows the blocked count`() {
    setContent(PrivacySettingsState(blockedCount = 3))

    composeTestRule.onNodeWithText("3 contacts or groups").assertIsDisplayed()
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_BLOCKED).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.BlockedClicked)
  }

  @Test
  fun `toggling read receipts emits ReadReceiptsToggled`() {
    setContent(PrivacySettingsState(readReceipts = true))

    scrollTo(PrivacySettingsTestTags.ROW_READ_RECEIPTS)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_READ_RECEIPTS).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ReadReceiptsToggled(false))
  }

  @Test
  fun `toggling typing indicators emits TypingIndicatorsToggled`() {
    setContent(PrivacySettingsState(typingIndicators = false))

    scrollTo(PrivacySettingsTestTags.ROW_TYPING_INDICATORS)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_TYPING_INDICATORS).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.TypingIndicatorsToggled(true))
  }

  @Test
  fun `clicking the default timer row emits DefaultTimerClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_DEFAULT_TIMER).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DefaultTimerClicked)
  }

  @Test
  fun `toggling biometric screen lock emits its requested state`() {
    setContent(PrivacySettingsState(biometricScreenLock = false))

    scrollTo(PrivacySettingsTestTags.ROW_BIOMETRIC_SCREEN_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_BIOMETRIC_SCREEN_LOCK).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.BiometricScreenLockToggled(true))
  }

  @Test
  fun `passphrase controls emit Molly actions independently of biometric lock`() {
    setContent(
      PrivacySettingsState(
        passphraseLock = true,
        passphraseLockTriggerValues = setOf("timeout"),
        passphraseLockTriggerOptions = listOf(PassphraseLockTriggerOption("timeout", "On inactivity timeout")),
        isPassphraseLockTimeoutEnabled = true
      )
    )

    scrollTo(PrivacySettingsTestTags.ROW_BIOMETRIC_SCREEN_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_BIOMETRIC_SCREEN_LOCK).assertExists()
    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_AUTOMATIC_LOCKDOWN)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_AUTOMATIC_LOCKDOWN).performClick()
    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT).performClick()

    assertThat(interactionEvents).containsExactly(
      PrivacySettingsEvent.PassphraseLockToggled(false),
      PrivacySettingsEvent.ChangePassphraseClicked,
      PrivacySettingsEvent.AutomaticLockdownClicked,
      PrivacySettingsEvent.PassphraseLockTimeoutClicked
    )
  }

  @Test
  fun `passphrase subordinate controls are disabled while encryption is off`() {
    setContent(PrivacySettingsState(passphraseLock = false, isPassphraseLockTimeoutEnabled = true))

    scrollTo(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE).assertIsNotEnabled()
    scrollTo(PrivacySettingsTestTags.ROW_AUTOMATIC_LOCKDOWN)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_AUTOMATIC_LOCKDOWN).assertIsNotEnabled()
    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT).assertIsNotEnabled()
  }

  @Test
  fun `zero passphrase timeout displays instant`() {
    setContent(PrivacySettingsState(passphraseLock = true, isPassphraseLockTimeoutEnabled = true, passphraseLockTimeout = Duration.ZERO))

    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT)
    composeTestRule.onNodeWithText("Instant").assertIsDisplayed()
  }

  @Test
  fun `positive passphrase timeout uses the existing duration formatter`() {
    setContent(PrivacySettingsState(passphraseLock = true, isPassphraseLockTimeoutEnabled = true, passphraseLockTimeout = 5.minutes))

    scrollTo(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT)
    composeTestRule.onNodeWithText("5 minutes").assertIsDisplayed()
  }

  @Test
  fun `forced screen security cannot be toggled off independently`() {
    setContent(PrivacySettingsState(screenSecurity = false, isScreenSecurityForced = true))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_SECURITY)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_SECURITY).assertIsNotEnabled()
  }

  @Test
  fun `toggling screen security emits ScreenSecurityToggled`() {
    setContent(PrivacySettingsState(screenSecurity = false))

    scrollTo(PrivacySettingsTestTags.ROW_SCREEN_SECURITY)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_SCREEN_SECURITY).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.ScreenSecurityToggled(true))
  }

  @Test
  fun `toggling incognito keyboard emits IncognitoKeyboardToggled`() {
    setContent(PrivacySettingsState(incognitoKeyboard = true))

    scrollTo(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.IncognitoKeyboardToggled(false))
  }

  @Test
  fun `payment controls stay removed from Molly privacy settings`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.ROW_ADVANCED)
    composeTestRule.onNodeWithText("Payments").assertDoesNotExist()
    composeTestRule.onNodeWithText("Payment lock").assertDoesNotExist()
  }

  @Test
  fun `clicking the advanced row emits AdvancedClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.ROW_ADVANCED)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.ROW_ADVANCED).performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.AdvancedClicked)
  }

  @Test
  fun `confirming automatic lockdown emits the selected triggers`() {
    setContent(
      PrivacySettingsState(
        passphraseLock = true,
        passphraseLockTriggerOptions = listOf(PassphraseLockTriggerOption("timeout", "On inactivity timeout")),
        dialog = PrivacySettingsState.Dialog.AutomaticLockdown
      )
    )

    composeTestRule.onNodeWithText("On inactivity timeout").performClick()
    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(interactionEvents).containsExactly(
      PrivacySettingsEvent.AutomaticLockdownSelected(setOf("timeout")),
      PrivacySettingsEvent.DialogDismissed
    )
  }

  @Test
  fun `the default timer row shows the abbreviated timer`() {
    setContent(PrivacySettingsState(universalExpireTimer = 1.days))

    scrollTo(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
    composeTestRule.onNodeWithText("1d").assertIsDisplayed()
  }

  @Test
  fun `clicking learn more under incognito keyboard emits IncognitoKeyboardLearnMoreClicked`() {
    setContent(PrivacySettingsState())

    scrollTo(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER)
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER).performFirstLinkClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked)
  }

  @Test
  fun `cancelling automatic lockdown emits DialogDismissed`() {
    setContent(PrivacySettingsState(passphraseLock = true, dialog = PrivacySettingsState.Dialog.AutomaticLockdown))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.DialogDismissed)
  }

  @Test
  fun `a snackbar in state is shown and then reported as dismissed`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.BiometricsUnavailable))

    composeTestRule.onNodeWithText("No biometric features available on this device").assertIsDisplayed()
    composeTestRule.waitUntil(timeoutMillis = 10_000) { interactionEvents.isNotEmpty() }

    assertThat(interactionEvents).containsExactly(PrivacySettingsEvent.SnackbarDismissed)
  }

  @Test
  fun `the biometric enrollment snackbar asks the user to enroll`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.BiometricEnrollmentRequired))

    composeTestRule.onNodeWithText("Please set up your biometrics in Android settings first").assertIsDisplayed()
  }

  @Test
  fun `the settings unavailable snackbar explains the failure`() {
    setContent(PrivacySettingsState(snackbar = PrivacySettingsState.Snackbar.DeviceSecuritySettingsUnavailable))

    composeTestRule.onNodeWithText("Failed to navigate to system settings").assertIsDisplayed()
  }

  private fun setContent(state: PrivacySettingsState) {
    composeTestRule.setContent {
      // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        PrivacySettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(PrivacySettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
