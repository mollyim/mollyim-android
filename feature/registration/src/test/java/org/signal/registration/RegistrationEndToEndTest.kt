/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags
import java.time.Duration

/**
 * End-to-end tests for the registration flow: renders the full [RegistrationNavHost] with a real
 * [RegistrationRepository] backed by in-memory fake controllers, and drives it by interacting with
 * the UI the way a user would.
 */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationEndToEndTest {

  companion object {
    private const val PHONE_NUMBER = "5550123456"
    private const val VERIFICATION_CODE = "123456"
    private const val PIN = "9182"
    private const val WAIT_TIMEOUT_MS = 30_000L
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private lateinit var networkController: FakeNetworkController
  private lateinit var storageController: FakeStorageController
  private lateinit var repository: RegistrationRepository
  private lateinit var viewModel: RegistrationViewModel

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())

    val context = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(context).grantPermissions(*RegistrationPermissions.getRequiredPermissions(context).toTypedArray())

    networkController = FakeNetworkController(correctVerificationCode = VERIFICATION_CODE)
    storageController = FakeStorageController()
    repository = RegistrationRepository(context, networkController, storageController, isLinkAndSyncAvailable = false)
    viewModel = RegistrationViewModel(repository, SavedStateHandle())
  }

  @Test
  fun `happy path - new registration by entering phone number, verification code, and creating a pin`() {
    var registrationComplete = false

    composeTestRule.setContent {
      SignalTheme {
        RegistrationNavHost(
          registrationRepository = repository,
          registrationViewModel = viewModel,
          permissionsState = createMockPermissionsState(),
          onRegistrationComplete = { registrationComplete = true }
        )
      }
    }

    // Welcome -> PhoneNumberEntry (permissions are already granted, so the permissions screen is skipped)
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // Enter the phone number and confirm it in the dialog
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextInput(PHONE_NUMBER)
    waitForTag(TestTags.PHONE_NUMBER_NEXT_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    // Session is created and a code is requested, landing us on the verification code screen
    waitForTag(TestTags.VERIFICATION_CODE_DIGIT_0)
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_0).performTextInput(VERIFICATION_CODE)

    // The code verifies and the account registers, landing us on PIN creation
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    // Wait for the confirm step's fresh input field to fully replace the create step's
    waitFor("PIN confirmation step") {
      composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_INPUT).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    // The PIN is backed up to SVR and registration completes
    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == "+1$PHONE_NUMBER") { "Expected committed e164 +1$PHONE_NUMBER but was ${committed.e164}" }
    assert(committed.aci.isNotEmpty()) { "Expected committed ACI to be populated" }
    assert(committed.pni.isNotEmpty()) { "Expected committed PNI to be populated" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(committed.accountEntropyPool.isNotEmpty()) { "Expected committed AEP to be populated" }

    assert(networkController.sessionCreated) { "Expected a verification session to be created" }
    assert(networkController.verificationCodeRequested) { "Expected a verification code to be requested" }
    assert(networkController.registeredE164 == "+1$PHONE_NUMBER") { "Expected registration for +1$PHONE_NUMBER but was ${networkController.registeredE164}" }
    assert(networkController.svrPin == PIN) { "Expected pin $PIN on SVR but was ${networkController.svrPin}" }
    assert(networkController.accountAttributesSyncJobEnqueued) { "Expected the account attributes sync job to be enqueued" }

    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  /**
   * Waits for [condition] to become true, pumping the main looper so that work scheduled by coroutines resuming from
   * background dispatchers (the real repository hops through Dispatchers.IO) gets executed. Advancing the looper clock
   * also completes animations and any short delay-based timeouts in the flow.
   */
  private fun waitFor(description: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
    while (true) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
      composeTestRule.waitForIdle()
      if (condition()) {
        return
      }
      if (System.currentTimeMillis() > deadline) {
        throw AssertionError("Timed out waiting for $description")
      }
      Thread.sleep(10)
    }
  }

  private fun waitForTag(tag: String) {
    waitFor("node with tag $tag") {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun createMockPermissionsState(): MockMultiplePermissionsState {
    return MockMultiplePermissionsState(
      allPermissionsGranted = true,
      permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = false).map { MockPermissionsState(it) }
    )
  }
}
