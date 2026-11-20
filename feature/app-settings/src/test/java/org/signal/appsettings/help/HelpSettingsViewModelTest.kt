/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpSettingsViewModelTest {

  companion object {
    private const val VERSION_NAME = "8.33.0"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private var logEnabled = false

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    logEnabled = false
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state contains the version name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(HelpSettingsState(versionName = VERSION_NAME))
  }

  @Test
  fun `WebsiteClicked opens Molly's website`() = assertEventEmits(HelpSettingsEvent.WebsiteClicked, HelpSettingsAction.OpenWebsite)

  @Test
  fun `VersionClicked opens app updates`() = assertEventEmits(HelpSettingsEvent.VersionClicked, HelpSettingsAction.NavigateToAppUpdates)

  @Test
  fun `enabling logging persists and exposes the choice`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(HelpSettingsEvent.DebugLoggingChanged(true))

    assertThat(logEnabled).isTrue()
    assertThat(viewModel.state.value.logEnabled).isTrue()
  }

  @Test
  fun `disabling logging requires confirmation`() = assertEventEmits(HelpSettingsEvent.DebugLoggingChanged(false), HelpSettingsAction.ConfirmDisableDebugLogging)

  @Test
  fun `disabling logging persists only after confirmation`() = runTest(testDispatcher) {
    logEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(HelpSettingsEvent.DebugLoggingChanged(false))
    assertThat(logEnabled).isTrue()

    viewModel.onEvent(HelpSettingsEvent.DisableDebugLoggingConfirmed)
    assertThat(logEnabled).isFalse()
    assertThat(viewModel.state.value.logEnabled).isFalse()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventEmits(HelpSettingsEvent.NavigateBackClicked, HelpSettingsAction.NavigateBack)

  @Test
  fun `SupportCenterClicked opens the support center`() = assertEventEmits(HelpSettingsEvent.SupportCenterClicked, HelpSettingsAction.OpenSupportCenter)

  @Test
  fun `ContactUsClicked navigates to contact us`() = assertEventEmits(HelpSettingsEvent.ContactUsClicked, HelpSettingsAction.NavigateToContactUs)

  @Test
  fun `VersionLongClicked copies the version name`() = assertEventEmits(HelpSettingsEvent.VersionLongClicked, HelpSettingsAction.CopyVersionToClipboard(VERSION_NAME))

  @Test
  fun `DebugLogClicked navigates to the debug log`() = assertEventEmits(HelpSettingsEvent.DebugLogClicked, HelpSettingsAction.NavigateToDebugLog)

  @Test
  fun `LicensesClicked navigates to licenses`() = assertEventEmits(HelpSettingsEvent.LicensesClicked, HelpSettingsAction.NavigateToLicenses)

  @Test
  fun `TermsAndPrivacyPolicyClicked opens the terms and privacy policy`() = assertEventEmits(HelpSettingsEvent.TermsAndPrivacyPolicyClicked, HelpSettingsAction.OpenTermsAndPrivacyPolicy)

  private fun assertEventEmits(event: HelpSettingsEvent, expected: HelpSettingsAction) = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
  }

  private fun createViewModel(): HelpSettingsViewModel = HelpSettingsViewModel(
    getLogEnabled = { logEnabled },
    setLogEnabled = { logEnabled = it },
    versionName = VERSION_NAME
  )

  private fun TestScope.collectActions(actions: Flow<HelpSettingsAction>): List<HelpSettingsAction> {
    val collected = mutableListOf<HelpSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
