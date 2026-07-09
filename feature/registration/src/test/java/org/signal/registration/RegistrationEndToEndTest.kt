/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags
import java.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * End-to-end tests for the registration flow: renders the full [RegistrationNavHost] with a real
 * [RegistrationRepository] backed by in-memory fake controllers, and drives it by interacting with
 * the UI the way a user would.
 *
 * The fakes default to a happy path. To exercise other navigation paths, override the relevant
 * response handler on [networkController] or state on [storageController] before driving the UI.
 */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationEndToEndTest {

  companion object {
    private const val PHONE_NUMBER = "5550123456"
    private const val E164 = "+1$PHONE_NUMBER"
    private const val VERIFICATION_CODE = FakeNetworkController.DEFAULT_VERIFICATION_CODE
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

  private val backupFolderUri: Uri = Uri.parse("content://test/backups")

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())

    val context = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(context).grantPermissions(*RegistrationPermissions.getRequiredPermissions(context).toTypedArray())

    networkController = FakeNetworkController()
    storageController = FakeStorageController()
    repository = RegistrationRepository(context, networkController, storageController, isLinkAndSyncAvailable = false)
    viewModel = RegistrationViewModel(repository, SavedStateHandle())
  }

  @Test
  fun `happy path - new registration by entering phone number, verification code, and creating a pin`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == E164) { "Expected committed e164 $E164 but was ${committed.e164}" }
    assert(committed.aci.isNotEmpty()) { "Expected committed ACI to be populated" }
    assert(committed.pni.isNotEmpty()) { "Expected committed PNI to be populated" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(committed.accountEntropyPool.isNotEmpty()) { "Expected committed AEP to be populated" }

    assert(networkController.lastCreateSessionE164 == E164) { "Expected a session for $E164 but was ${networkController.lastCreateSessionE164}" }
    assert(networkController.lastRegisterAccountRequest?.e164 == E164) { "Expected registration for $E164 but was ${networkController.lastRegisterAccountRequest}" }
    assert(networkController.lastSetPinRequest?.pin == PIN) { "Expected pin $PIN on SVR but was ${networkController.lastSetPinRequest?.pin}" }
    assert(networkController.accountAttributesSyncJobEnqueued) { "Expected the account attributes sync job to be enqueued" }

    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a registration lock is unlocked by entering the existing pin and registration completes`() {
    val masterKey = MasterKey(ByteArray(32) { it.toByte() })

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == null) {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      } else {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      }
    }

    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(masterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The account is reglocked, so the user must prove they know their existing PIN
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected master key restore with pin $PIN but was ${networkController.lastRestoreMasterKeyRequest}" }
    assert(networkController.lastRegisterAccountRequest?.registrationLock == masterKey.deriveRegistrationLock()) { "Expected registration with the derived reglock token" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == E164) { "Expected committed e164 $E164 but was ${committed.e164}" }
    assert(committed.aci.isNotEmpty()) { "Expected committed ACI to be populated" }
  }

  @Test
  fun `re-registering an existing account offers restore selection, which can be skipped to complete registration`() {
    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore. Decline it, confirming the skip warning.
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_NONE).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == E164) { "Expected committed e164 $E164 but was ${committed.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `opting out of creating a pin still completes registration`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // Instead of creating a PIN, disable PINs via the overflow menu, confirming the warning
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_MENU_BUTTON).performClick()
    waitForTag(TestTags.PIN_CREATION_DISABLE_PIN_MENU_ITEM)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_DISABLE_PIN_MENU_ITEM).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.pinOptedOut) { "Expected the committed data to record the PIN opt-out" }
    assert(committed.pin.isEmpty()) { "Expected no committed pin but was ${committed.pin}" }
    assert(networkController.lastSetPinRequest == null) { "Should not have backed up a pin to SVR" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a remote backup before registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected registration via the recovery password derived from the entered AEP"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == E164) { "Expected committed e164 $E164 but was ${committed.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup before registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // With the backup restored, registration happens via the recovery password and the user creates a PIN
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected registration via the recovery password derived from the restored AEP"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.e164 == E164) { "Expected committed e164 $E164 but was ${committed.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one from the restored backup" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `restoring a remote backup after registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterAep(aep)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup after registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    // The backup contains the user's PIN, so the flow finishes without any PIN screens
    storageController.onRestoreLocalBackupV2 = { _, _ ->
      flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    restoreLocalBackup(aep)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  // -- Flow helpers: each one drives the UI from the screen the flow is currently on.

  /**
   * @param folderPickerResult When set, any system activity launched for a result (i.e. the backup folder picker)
   *   is immediately answered with this value.
   */
  private fun launchRegistrationFlow(
    folderPickerResult: Uri? = null,
    onRegistrationComplete: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      SignalTheme {
        ActivityResultInterceptor(folderPickerResult) {
          RegistrationNavHost(
            registrationRepository = repository,
            registrationViewModel = viewModel,
            permissionsState = createMockPermissionsState(),
            onRegistrationComplete = onRegistrationComplete
          )
        }
      }
    }
  }

  @Composable
  private fun ActivityResultInterceptor(result: Any?, content: @Composable () -> Unit) {
    if (result == null) {
      content()
    } else {
      val owner = remember { ImmediateResultRegistryOwner(result) }
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        content()
      }
    }
  }

  /** From the Welcome screen: continues to phone number entry (permissions are granted, so that screen is skipped), enters [PHONE_NUMBER], and confirms the dialog. */
  private fun submitPhoneNumber() {
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()
    enterPhoneNumber()
  }

  /** From the phone number entry screen: enters [PHONE_NUMBER] and confirms the dialog. */
  private fun enterPhoneNumber() {
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextInput(PHONE_NUMBER)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()

    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()
  }

  /** From the Welcome screen: navigates to manual restore selection via "restore or transfer" → "don't have my old phone". */
  private fun startManualRestore() {
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    waitForTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON).performClick()
  }

  /** From the archive restore selection screen: picks the restore option with the given tag. */
  private fun chooseRestoreOption(optionTag: String) {
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    composeTestRule.onNodeWithTag(optionTag).performClick()
  }

  /** From the AEP entry screen: types the backup key and submits it. */
  private fun enterAep(aep: AccountEntropyPool) {
    waitForTag(TestTags.ENTER_AEP_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_INPUT).performTextInput(aep.value)
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).performClick()
  }

  /** From the local backup restore screen: picks the backup folder (answered by the fake folder picker), restores the backup that is found, and decrypts it with [aep]. */
  private fun restoreLocalBackup(aep: AccountEntropyPool) {
    waitForTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON).performClick()
    waitForTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON).performClick()
    enterAep(aep)
  }

  /** From the remote restore screen: starts the restore once the backup info has loaded. */
  private fun startRemoteRestore() {
    waitForTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON).performClick()
  }

  /** From the verification code screen: enters all six digits of [code], which submits automatically. */
  private fun submitVerificationCode(code: String) {
    waitForTag(TestTags.VERIFICATION_CODE_DIGIT_0)
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_0).performTextInput(code)
  }

  /** From the PIN creation screen: enters [pin], then re-enters it on the confirmation step. */
  private fun createPin(pin: String) {
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextInput(pin)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    // Wait for the confirm step's fresh input field to fully replace the create step's
    waitFor("PIN confirmation step") {
      composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_INPUT).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).performTextInput(pin)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()
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

/**
 * An [ActivityResultRegistryOwner] that immediately answers any launched contract (e.g. the system folder picker)
 * with [result], since no real activity can handle intents in a unit test.
 */
private class ImmediateResultRegistryOwner(private val result: Any?) : ActivityResultRegistryOwner {
  override val activityResultRegistry = object : ActivityResultRegistry() {
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      dispatchResult(requestCode, result)
    }
  }
}
