/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.molly.app.base.ApkInfo
import org.signal.appsettings.help.HelpSettingsAction
import org.signal.appsettings.help.HelpSettingsEvent
import org.signal.appsettings.help.HelpSettingsScreen
import org.signal.appsettings.help.HelpSettingsViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.signal.core.util.Util
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

class HelpSettingsFragment : ComposeFragment() {

  private val viewModel: HelpSettingsViewModel by viewModel {
    HelpSettingsViewModel(
      versionName = ApkInfo.versionName,
      getLogEnabled = { SignalStore.molly.logEnabled },
      setLogEnabled = { SignalStore.molly.logEnabled = it }
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    HelpSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  private fun handleAction(action: HelpSettingsAction) {
    when (action) {
      HelpSettingsAction.OpenWebsite -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.website_url))
      HelpSettingsAction.NavigateBack -> findNavController().popBackStack()
      HelpSettingsAction.OpenSupportCenter -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
      HelpSettingsAction.NavigateToContactUs -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_helpFragment)
      HelpSettingsAction.NavigateToAppUpdates -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_appUpdatesFragment)
      HelpSettingsAction.ConfirmDisableDebugLogging -> {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.HelpSettingsFragment_disable_and_delete_debug_log)
          .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.onEvent(HelpSettingsEvent.DisableDebugLoggingConfirmed) }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      }
      is HelpSettingsAction.CopyVersionToClipboard -> {
        Util.copyToClipboard(requireContext(), action.versionName)
        Toast.makeText(requireContext(), AppSettingsR.string.HelpSettingsFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
      }
      HelpSettingsAction.NavigateToDebugLog -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
      HelpSettingsAction.NavigateToLicenses -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
      HelpSettingsAction.OpenTermsAndPrivacyPolicy -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.terms_and_privacy_policy_url))
    }
  }
}
