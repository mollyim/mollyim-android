/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.privacy.PassphraseDialogMode
import org.signal.appsettings.privacy.PassphraseLockTriggerOption
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsState.Dialog
import org.signal.appsettings.privacy.PrivacySettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var blockUnknown = false
  private var readReceipts = false
  private var typingIndicators = false
  private var screenSecurity = false
  private var incognitoKeyboard = false
  private var passphraseLock = false
  private var passphraseLockTriggers: Set<String> = setOf("timeout")
  private var passphraseLockTimeout: Duration = 5.minutes
  private var biometricScreenLock = false
  private var screenSecurityForced = false
  private val triggerOptions = listOf(PassphraseLockTriggerOption("timeout", "On inactivity timeout"))
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(PrivacySettingsRepository)

    coEvery { PrivacySettingsRepository.getBlockedCount() } returns 4
    every { PrivacySettingsRepository.hasPhoneNumber() } returns true
    every { PrivacySettingsRepository.isBlockUnknownEnabled() } answers { blockUnknown }
    every { PrivacySettingsRepository.setBlockUnknownEnabled(any()) } answers { blockUnknown = firstArg() }
    every { PrivacySettingsRepository.isReadReceiptsEnabled() } answers { readReceipts }
    coEvery { PrivacySettingsRepository.setReadReceiptsEnabled(any()) } answers { readReceipts = firstArg() }
    every { PrivacySettingsRepository.isTypingIndicatorsEnabled() } answers { typingIndicators }
    coEvery { PrivacySettingsRepository.setTypingIndicatorsEnabled(any()) } answers { typingIndicators = firstArg() }
    every { PrivacySettingsRepository.getUniversalExpireTimer() } returns 1.days
    every { PrivacySettingsRepository.isPassphraseLockEnabled() } answers { passphraseLock }
    every { PrivacySettingsRepository.setPassphraseLockEnabled(any()) } answers { passphraseLock = firstArg() }
    every { PrivacySettingsRepository.getPassphraseLockTriggers() } answers { passphraseLockTriggers }
    every { PrivacySettingsRepository.isPassphraseLockTimeoutEnabled() } answers { "timeout" in passphraseLockTriggers }
    every { PrivacySettingsRepository.setPassphraseLockTriggers(any()) } answers { passphraseLockTriggers = firstArg() }
    every { PrivacySettingsRepository.getPassphraseLockTimeout() } answers { passphraseLockTimeout }
    every { PrivacySettingsRepository.setPassphraseLockTimeout(any()) } answers { passphraseLockTimeout = firstArg<Long>().seconds }
    every { PrivacySettingsRepository.getPassphraseLockTriggerOptions() } returns triggerOptions
    every { PrivacySettingsRepository.isBiometricScreenLockEnabled() } answers { biometricScreenLock }
    every { PrivacySettingsRepository.setBiometricScreenLockEnabled(any()) } answers { biometricScreenLock = firstArg() }
    every { PrivacySettingsRepository.isScreenSecurityForced() } answers { screenSecurityForced }
    every { PrivacySettingsRepository.isScreenSecurityEnabled() } answers { screenSecurity }
    every { PrivacySettingsRepository.setScreenSecurityEnabled(any()) } answers { screenSecurity = firstArg() }
    every { PrivacySettingsRepository.isIncognitoKeyboardEnabled() } answers { incognitoKeyboard }
    every { PrivacySettingsRepository.setIncognitoKeyboardEnabled(any()) } answers { incognitoKeyboard = firstArg() }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(PrivacySettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    readReceipts = true

    val state = createViewModel().state.value

    assertThat(state.readReceipts).isTrue()
    assertThat(state.universalExpireTimer).isEqualTo(1.days)
    assertThat(state.hasPhoneNumber).isTrue()
    assertThat(state.passphraseLockTriggerValues).isEqualTo(setOf("timeout"))
    assertThat(state.passphraseLockTimeout).isEqualTo(5.minutes)
  }

  @Test
  fun `ScreenResumed loads the blocked count`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.blockedCount).isEqualTo(4)
  }

  @Test
  fun `ScreenResumed picks up settings changed while away`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    passphraseLock = true
    biometricScreenLock = true
    screenSecurityForced = true

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.passphraseLock).isTrue()
    assertThat(viewModel.state.value.biometricScreenLock).isTrue()
    assertThat(viewModel.state.value.isScreenSecurityEnabled).isTrue()
  }

  @Test
  fun `toggling a setting does not navigate`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `ReadReceiptsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))

    coVerify { PrivacySettingsRepository.setReadReceiptsEnabled(true) }
    assertThat(viewModel.state.value.readReceipts).isTrue()
  }

  @Test
  fun `TypingIndicatorsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(true))

    coVerify { PrivacySettingsRepository.setTypingIndicatorsEnabled(true) }
    assertThat(viewModel.state.value.typingIndicators).isTrue()
  }

  @Test
  fun `BlockUnknownToggled keeps the restored fork setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.BlockUnknownToggled(true))

    verify { PrivacySettingsRepository.setBlockUnknownEnabled(true) }
    assertThat(viewModel.state.value.blockUnknown).isTrue()
  }

  @Test
  fun `ScreenSecurityToggled stores the setting and applies it to the window`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(viewModel.state.value.screenSecurity).isTrue()
    assertThat(actions).containsExactly(PrivacySettingsAction.ApplyScreenSecurity(true))
  }

  @Test
  fun `IncognitoKeyboardToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardToggled(true))

    assertThat(viewModel.state.value.incognitoKeyboard).isTrue()
  }

  @Test
  fun `passphrase toggles launch native dialogs without changing the preference`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockToggled(false))

    assertThat(actions).containsExactly(
      PrivacySettingsAction.LaunchPassphraseDialog(PassphraseDialogMode.ENABLE),
      PrivacySettingsAction.LaunchPassphraseDialog(PassphraseDialogMode.DISABLE)
    )
    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockEnabled(any()) }
  }

  @Test
  fun `successful native dialog completion changes the real passphrase preference`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.completePassphraseLockChange(true)

    verify { PrivacySettingsRepository.setPassphraseLockEnabled(true) }
    assertThat(viewModel.state.value.passphraseLock).isTrue()
  }

  @Test
  fun `ChangePassphraseClicked launches the native change flow`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.LaunchPassphraseDialog(PassphraseDialogMode.CHANGE))
  }

  @Test
  fun `automatic lockdown stores the confirmed trigger set`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.AutomaticLockdownClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.AutomaticLockdown)
    viewModel.onEvent(PrivacySettingsEvent.AutomaticLockdownSelected(emptySet()))

    verify { PrivacySettingsRepository.setPassphraseLockTriggers(emptySet()) }
    assertThat(viewModel.state.value.passphraseLockTriggerValues).isEmpty()
    assertThat(viewModel.state.value.isPassphraseLockTimeoutEnabled).isFalse()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `automatic lockdown selection is ignored after dismissal`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.AutomaticLockdownClicked)
    viewModel.onEvent(PrivacySettingsEvent.DialogDismissed)
    viewModel.onEvent(PrivacySettingsEvent.AutomaticLockdownSelected(emptySet()))

    verify(exactly = 0) { PrivacySettingsRepository.setPassphraseLockTriggers(any()) }
  }

  @Test
  fun `timeout row launches Molly native picker with the current value`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.LaunchPassphraseLockTimeoutPicker(5.minutes))
  }

  @Test
  fun `selected passphrase timeout is stored as seconds including instant`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutSelected(2.hours))
    verify { PrivacySettingsRepository.setPassphraseLockTimeout(2.hours.inWholeSeconds) }
    assertThat(viewModel.state.value.passphraseLockTimeout).isEqualTo(2.hours)

    viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutSelected(Duration.ZERO))
    verify { PrivacySettingsRepository.setPassphraseLockTimeout(0) }
    assertThat(viewModel.state.value.passphraseLockTimeout).isEqualTo(Duration.ZERO)
  }

  @Test
  fun `biometric enable waits for authentication before applying state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockToggled(true))

    assertThat(actions).containsExactly(PrivacySettingsAction.EnableBiometricScreenLock)
    verify(exactly = 0) { PrivacySettingsRepository.setBiometricScreenLockEnabled(any()) }

    viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockEnableAuthenticated)

    verify { PrivacySettingsRepository.setBiometricScreenLockEnabled(true) }
    assertThat(viewModel.state.value.biometricScreenLock).isTrue()
    assertThat(actions).containsExactly(
      PrivacySettingsAction.EnableBiometricScreenLock,
      PrivacySettingsAction.ApplyBiometricScreenLock(true)
    )
  }

  @Test
  fun `biometric disable applies immediately`() = runTest(testDispatcher) {
    biometricScreenLock = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockToggled(false))

    verify { PrivacySettingsRepository.setBiometricScreenLockEnabled(false) }
    assertThat(viewModel.state.value.biometricScreenLock).isFalse()
    assertThat(actions).containsExactly(PrivacySettingsAction.ApplyBiometricScreenLock(false))
  }

  @Test
  fun `biometric feedback is shown without changing the preference`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockUnavailable)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.BiometricsUnavailable)
    verify(exactly = 0) { PrivacySettingsRepository.setBiometricScreenLockEnabled(any()) }

    viewModel.onEvent(PrivacySettingsEvent.SnackbarDismissed)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)

    viewModel.onEvent(PrivacySettingsEvent.BiometricEnrollmentRequired)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.BiometricEnrollmentRequired)
  }

  @Test
  fun `DeviceSecuritySettingsUnavailable tells the user`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DeviceSecuritySettingsUnavailable)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.DeviceSecuritySettingsUnavailable)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `row clicks navigate only to their valid fork routes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PhoneNumberClicked)
    viewModel.onEvent(PrivacySettingsEvent.BlockedClicked)
    viewModel.onEvent(PrivacySettingsEvent.DefaultTimerClicked)
    viewModel.onEvent(PrivacySettingsEvent.AdvancedClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.BlockedUsers),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.ExpiringMessages),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.Advanced)
    )
    assertThat(actions).isEmpty()
  }

  @Test
  fun `IncognitoKeyboardLearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.OpenIncognitoKeyboardLearnMore)
  }

  private fun createViewModel(): PrivacySettingsViewModel {
    return PrivacySettingsViewModel(
      parentEventEmitter = { navigationEvents += it }
    )
  }

  private fun TestScope.collectActions(actions: Flow<PrivacySettingsAction>): List<PrivacySettingsAction> {
    val collected = mutableListOf<PrivacySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
