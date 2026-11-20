/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.privacy.PassphraseDialogMode
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import org.thoughtcrime.securesms.components.TimeDurationPickerDialog
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.WindowUtil
import kotlin.time.Duration.Companion.seconds
import org.signal.appsettings.R as AppSettingsR

private val TAG = Log.tag(PrivacySettingsViewModel::class)
private const val PASSPHRASE_LOCK_TIMEOUT_DIALOG_TAG = "PassphraseLockTimeoutDialog"

/**
 * Hosts [PrivacySettingsScreen] and carries out its [PrivacySettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun PrivacySettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: PrivacySettingsViewModel = viewModel {
    PrivacySettingsViewModel(parentEventEmitter = parentEventEmitter)
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current
  val fragmentActivity = activity as? FragmentActivity
  val lifecycleOwner = LocalLifecycleOwner.current

  fun authenticateBiometrics() {
    fragmentActivity?.let { authenticateBiometricScreenLock(it, viewModel) }
  }

  val biometricEnrollment = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    authenticateBiometrics()
  }

  DisposableEffect(fragmentActivity, lifecycleOwner, viewModel) {
    fragmentActivity?.supportFragmentManager?.setFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION, lifecycleOwner) { _, bundle ->
      viewModel.onEvent(PrivacySettingsEvent.PassphraseLockTimeoutSelected(bundle.getLong(TimeDurationPickerDialog.RESULT_KEY_DURATION_SECONDS).seconds))
    }

    onDispose {
      fragmentActivity?.supportFragmentManager?.clearFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION)
    }
  }

  val incognitoLearnMore = stringResource(AppSettingsR.string.preferences__incognito_keyboard_learn_more)
  CollectActions(viewModel.actions) { action ->
    when (action) {
      is PrivacySettingsAction.LaunchPassphraseDialog -> fragmentActivity?.let { launchPassphraseDialog(it, action.mode, viewModel) }
      is PrivacySettingsAction.LaunchPassphraseLockTimeoutPicker -> fragmentActivity?.let { launchPassphraseLockTimeoutPicker(it, action.timeout) }
      PrivacySettingsAction.EnableBiometricScreenLock -> enableBiometricScreenLock(fragmentActivity, biometricEnrollment, viewModel)
      is PrivacySettingsAction.ApplyBiometricScreenLock -> applyBiometricScreenLock(activity, action.enabled)
      PrivacySettingsAction.OpenIncognitoKeyboardLearnMore -> CommunicationActions.openBrowserLink(context, incognitoLearnMore)
      is PrivacySettingsAction.ApplyScreenSecurity -> applyScreenSecurity(activity, action.enabled)
    }
  }

  PrivacySettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

private fun launchPassphraseDialog(
  activity: FragmentActivity,
  mode: PassphraseDialogMode,
  viewModel: PrivacySettingsViewModel
) {
  val dialogMode = when (mode) {
    PassphraseDialogMode.ENABLE -> ChangePassphraseDialogFragment.MODE_ENABLE
    PassphraseDialogMode.DISABLE -> ChangePassphraseDialogFragment.MODE_DISABLE
    PassphraseDialogMode.CHANGE -> ChangePassphraseDialogFragment.MODE_CHANGE
  }

  val dialog = ChangePassphraseDialogFragment.newInstance(dialogMode)
  dialog.setMasterSecretChangedListener { masterSecret ->
    if (mode == PassphraseDialogMode.CHANGE) {
      masterSecret.close()
      Toast.makeText(activity, R.string.preferences__passphrase_changed, Toast.LENGTH_LONG).show()
    } else {
      viewModel.completePassphraseLockChange(mode == PassphraseDialogMode.ENABLE)
      (activity as PassphraseActivity).setMasterSecret(masterSecret)
      ConversationUtil.refreshRecipientShortcuts()
    }
  }
  dialog.show(activity.supportFragmentManager, "ChangePassphraseDialogFragment")
}

private fun launchPassphraseLockTimeoutPicker(activity: FragmentActivity, timeout: kotlin.time.Duration) {
  val fragmentManager = activity.supportFragmentManager
  fragmentManager.clearFragmentResult(TimeDurationPickerDialog.RESULT_DURATION)
  TimeDurationPickerDialog.create(timeout).show(fragmentManager, PASSPHRASE_LOCK_TIMEOUT_DIALOG_TAG)
}

private fun enableBiometricScreenLock(
  activity: FragmentActivity?,
  enrollmentLauncher: ActivityResultLauncher<Intent>,
  viewModel: PrivacySettingsViewModel
) {
  if (activity == null) return

  val status = BiometricDialogFragment.checkBiometricAvailability(activity, BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED)
  when (status) {
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockUnavailable)

    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
      if (Build.VERSION.SDK_INT >= 30) {
        try {
          enrollmentLauncher.launch(
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
              Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
              BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED
            )
          )
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "Failed to navigate to biometric enrollment.", e)
          viewModel.onEvent(PrivacySettingsEvent.DeviceSecuritySettingsUnavailable)
        }
      } else {
        viewModel.onEvent(PrivacySettingsEvent.BiometricEnrollmentRequired)
      }
    }

    else -> authenticateBiometricScreenLock(activity, viewModel)
  }
}

private fun authenticateBiometricScreenLock(activity: FragmentActivity, viewModel: PrivacySettingsViewModel) {
  BiometricDialogFragment.authenticate(
    activity,
    object : BiometricDialogFragment.Listener {
      override fun onSuccess(): Boolean {
        viewModel.onEvent(PrivacySettingsEvent.BiometricScreenLockEnableAuthenticated)
        return true
      }

      override fun onError(errString: CharSequence): Boolean {
        Toast.makeText(activity, errString, Toast.LENGTH_LONG).show()
        return true
      }
    }
  )
}

private fun applyBiometricScreenLock(activity: Activity?, enabled: Boolean) {
  (activity as? BaseActivity)?.blockRecentAppsScreenshot(enabled)
  activity?.let { WindowUtil.initializeScreenshotSecurity(it, it.window) }
}

private fun applyScreenSecurity(activity: Activity?, enabled: Boolean) {
  val window = activity?.window ?: return

  if (enabled) {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  } else {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }
}
