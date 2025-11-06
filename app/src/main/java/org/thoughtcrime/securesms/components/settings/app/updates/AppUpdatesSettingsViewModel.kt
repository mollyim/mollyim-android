/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import android.app.Application
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.apkupdate.ApkUpdateNotifications
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

class AppUpdatesSettingsViewModel : ViewModel() {

  private val application: Application = AppDependencies.application

  private val internalState = MutableStateFlow(getState())

  val state: StateFlow<AppUpdatesSettingsState> = internalState

  fun setUpdateApkEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkEnabled(application, enabled)
    if (enabled) {
      checkForUpdates()
    }
    refresh()
  }

  fun setIncludeBetaEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkIncludeBetaEnabled(application, enabled)
    ApkUpdateNotifications.dismissInstallPrompt(application)
    checkForUpdates()
    refresh()
  }

  fun checkForUpdates() {
    ApkUpdateRefreshListener.scheduleIfAllowed(application)
    AppDependencies.jobManager.add(ApkUpdateJob())
  }

  fun refresh() {
    internalState.update { getState() }
  }

  private fun getState(): AppUpdatesSettingsState {
    return AppUpdatesSettingsState(
      lastCheckedTime = SignalStore.apkUpdate.lastSuccessfulCheck,
      includeBetaEnabled = TextSecurePreferences.isUpdateApkIncludeBetaEnabled(application),
      autoUpdateEnabled = TextSecurePreferences.isUpdateApkEnabled(application),
    )
  }
}
