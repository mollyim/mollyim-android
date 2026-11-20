/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [HelpSettingsScreen].
 */
class HelpSettingsViewModel(
  private val getLogEnabled: () -> Boolean,
  private val setLogEnabled: (Boolean) -> Unit,
  versionName: String
) : EventDrivenViewModel<HelpSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(HelpSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(HelpSettingsState(versionName = versionName, logEnabled = getLogEnabled()))
  private val _actions = Channel<HelpSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<HelpSettingsState> = _state.asStateFlow()
  val actions: Flow<HelpSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: HelpSettingsEvent) {
    when (event) {
      HelpSettingsEvent.WebsiteClicked -> _actions.send(HelpSettingsAction.OpenWebsite)
      HelpSettingsEvent.NavigateBackClicked -> _actions.send(HelpSettingsAction.NavigateBack)
      HelpSettingsEvent.SupportCenterClicked -> _actions.send(HelpSettingsAction.OpenSupportCenter)
      HelpSettingsEvent.ContactUsClicked -> _actions.send(HelpSettingsAction.NavigateToContactUs)
      HelpSettingsEvent.VersionClicked -> _actions.send(HelpSettingsAction.NavigateToAppUpdates)
      HelpSettingsEvent.VersionLongClicked -> _actions.send(HelpSettingsAction.CopyVersionToClipboard(_state.value.versionName))
      is HelpSettingsEvent.DebugLoggingChanged -> {
        if (event.enabled) {
          applyLogEnabled(true)
        } else {
          _actions.send(HelpSettingsAction.ConfirmDisableDebugLogging)
        }
      }
      HelpSettingsEvent.DisableDebugLoggingConfirmed -> applyLogEnabled(false)
      HelpSettingsEvent.DebugLogClicked -> _actions.send(HelpSettingsAction.NavigateToDebugLog)
      HelpSettingsEvent.LicensesClicked -> _actions.send(HelpSettingsAction.NavigateToLicenses)
      HelpSettingsEvent.TermsAndPrivacyPolicyClicked -> _actions.send(HelpSettingsAction.OpenTermsAndPrivacyPolicy)
    }
  }

  /** Reloads the externally stored logging choice when returning to the screen. */
  fun refresh() {
    _state.update { it.copy(logEnabled = getLogEnabled()) }
  }

  private fun applyLogEnabled(enabled: Boolean) {
    setLogEnabled(enabled)
    if (enabled) {
      Log.setLogging(true)
    } else {
      Log.disableAndWipeLogs()
    }
    refresh()
  }
}
