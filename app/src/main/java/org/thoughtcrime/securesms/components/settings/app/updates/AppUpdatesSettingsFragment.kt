/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

/**
 * Settings around app updates. Only shown for builds that manage their own app updates.
 */
class AppUpdatesSettingsFragment : ComposeFragment() {

  private val viewModel: AppUpdatesSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppUpdatesSettingsScreen(
      state = state,
      callbacks = remember { Callbacks() }
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  private inner class Callbacks : AppUpdatesSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAutoUpdateChanged(enabled: Boolean) {
      viewModel.setUpdateApkEnabled(enabled)
    }

    override fun onIncludeBetaChanged(enabled: Boolean) {
      viewModel.setIncludeBetaEnabled(enabled)
    }

    override fun onCheckForUpdatesClick() {
      viewModel.checkForUpdates()
    }
  }
}

private interface AppUpdatesSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onAutoUpdateChanged(enabled: Boolean) = Unit
  fun onIncludeBetaChanged(enabled: Boolean) = Unit
  fun onCheckForUpdatesClick() = Unit

  object Empty : AppUpdatesSettingsCallbacks
}

@Composable
private fun AppUpdatesSettingsScreen(
  state: AppUpdatesSettingsState,
  callbacks: AppUpdatesSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_updates__title),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { paddingValues ->

    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      if (!BuildConfig.MANAGE_MOLLY_UPDATES) {
        item {
          Rows.TextRow(
            text = {
              Text(
                text = stringResource(R.string.HelpSettingsFragment_for_updates_please_check_your_app_store),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          )
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.HelpSettingsFragment__version),
          label = BuildConfig.VERSION_NAME,
        )
      }

      if (BuildConfig.MANAGE_MOLLY_UPDATES) {
        item {
          Rows.ToggleRow(
            checked = state.autoUpdateEnabled,
            text = stringResource(R.string.preferences__autoupdate_molly),
            label = stringResource(R.string.preferences__periodically_check_for_new_releases_and_ask_to_install_them),
            onCheckChanged = callbacks::onAutoUpdateChanged
          )
        }

        item {
          Rows.ToggleRow(
            checked = state.includeBetaEnabled,
            text = stringResource(R.string.preferences__include_beta_updates),
            label = stringResource(R.string.preferences__beta_versions_are_intended_for_testing_purposes_and_may_contain_bugs),
            onCheckChanged = callbacks::onIncludeBetaChanged
          )
        }

        item {
          val (relTime, relTimeAccessible) = rememberCheckTime(state.lastCheckedTime)
          val label = stringResource(R.string.AppUpdatesSettingsFragment__last_checked_s, relTime)
          val contentDesc = relTimeAccessible?.let {
            stringResource(R.string.AppUpdatesSettingsFragment__last_checked_s, it)
          }
          Rows.TextRow(
            text = stringResource(R.string.EnableAppUpdatesMegaphone_check_for_updates),
            label = label,
            modifier = Modifier.semantics {
              contentDescription = contentDesc ?: label
            },
            enabled = state.autoUpdateEnabled,
            onClick = callbacks::onCheckForUpdatesClick
          )
        }
      }
    }
  }
}

@Composable
private fun rememberCheckTime(timestamp: Long): Pair<String, String?> {
  val context = LocalContext.current
  return remember(timestamp) {
    if (timestamp > 0) {
      DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), timestamp)
    } else {
      val never = context.getString(R.string.preferences__never)
      Pair(never, null)
    }
  }
}

@DayNightPreviews
@Composable
private fun AppUpdatesSettingsScreenPreview() {
  Previews.Preview {
    AppUpdatesSettingsScreen(
      state = AppUpdatesSettingsState(
        lastCheckedTime = System.currentTimeMillis(),
        includeBetaEnabled = true,
        autoUpdateEnabled = true
      ),
      callbacks = AppUpdatesSettingsCallbacks.Empty
    )
  }
}
