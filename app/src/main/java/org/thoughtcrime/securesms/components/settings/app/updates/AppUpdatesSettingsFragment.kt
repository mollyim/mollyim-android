/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import androidx.fragment.app.viewModels
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.help.HelpSettingsState
import org.thoughtcrime.securesms.components.settings.app.help.HelpSettingsViewModel
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.events.ApkUpdateEvent
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import java.util.Locale

/**
 * Settings around app updates. Only shown for builds that manage their own app updates.
 */
class AppUpdatesSettingsFragment : DSLSettingsFragment(R.string.preferences_app_updates__title) {

  private val viewModel: HelpSettingsViewModel by viewModels()

  override fun bindAdapter(adapter: MappingAdapter) {
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onApkUpdateEvent(event: ApkUpdateEvent) {
    viewModel.refreshState()
  }

  private fun getConfiguration(state: HelpSettingsState): DSLConfiguration {
    return configure {
      textPref(
        title = DSLSettingsText.from(R.string.HelpSettingsFragment__version),
        summary = DSLSettingsText.from(BuildConfig.VERSION_NAME)
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__autoupdate_molly),
        summary = DSLSettingsText.from(R.string.preferences__periodically_check_for_new_releases_and_ask_to_install_them),
        isChecked = state.updateApkEnabled,
        onClick = {
          viewModel.setUpdateApkEnabled(!state.updateApkEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__include_beta_updates),
        summary = DSLSettingsText.from(R.string.preferences__beta_versions_are_intended_for_testing_purposes_and_may_contain_bugs),
        isChecked = state.includeBetaEnabled,
        isEnabled = state.updateApkEnabled,
        onClick = {
          viewModel.setIncludeBetaEnabled(!state.includeBetaEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.EnableAppUpdatesMegaphone_check_for_updates),
        summary = DSLSettingsText.from(
          getString(R.string.AppUpdatesSettingsFragment__last_checked_s, formatCheckTime(state.lastUpdateCheckTime))
        ),
        isEnabled = state.updateApkEnabled,
        onClick = {
          viewModel.checkForUpdates()
        }
      )
    }
  }

  private fun formatCheckTime(timestamp: Long): String {
    return if (timestamp > 0) {
      DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), timestamp)
    } else {
      getString(R.string.preferences__never)
    }
  }
}
