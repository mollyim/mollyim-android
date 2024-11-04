package org.thoughtcrime.securesms.components.settings.app.help

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.apkupdate.ApkUpdateNotifications
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class HelpSettingsViewModel : ViewModel() {

  private val application: Application = AppDependencies.application

  private val store: Store<HelpSettingsState> = Store(getCurrentState())

  val state: LiveData<HelpSettingsState> = store.stateLiveData

  fun setUpdateApkEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkEnabled(application, enabled)
    if (enabled) {
      checkForUpdates()
    }
    refreshState()
  }

  fun setIncludeBetaEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkIncludeBetaEnabled(application, enabled)
    ApkUpdateNotifications.dismissInstallPrompt(application)
    checkForUpdates()
    refreshState()
  }

  fun checkForUpdates() {
    ApkUpdateRefreshListener.scheduleIfAllowed(application)
    AppDependencies.jobManager.add(ApkUpdateJob())
  }

  fun setLogEnabled(enabled: Boolean) {
    TextSecurePreferences.setLogEnabled(application, enabled)
    Log.setLogging(enabled)
    if (!enabled) {
      Log.wipeLogs()
    }
    refreshState()
  }

  fun refreshState() {
    store.update { getCurrentState() }
  }

  private fun getCurrentState(): HelpSettingsState {
    return HelpSettingsState(
      updateApkEnabled = TextSecurePreferences.isUpdateApkEnabled(application),
      includeBetaEnabled = TextSecurePreferences.isUpdateApkIncludeBetaEnabled(application),
      logEnabled = TextSecurePreferences.isLogEnabled(application),
      lastUpdateCheckTime = SignalStore.apkUpdate.lastSuccessfulCheck
    )
  }
}
