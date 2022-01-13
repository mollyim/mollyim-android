package org.thoughtcrime.securesms.components.settings.app.help

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.UpdateApkJob
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.UpdateApkReadyListener
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class HelpSettingsViewModel : ViewModel() {

  private val application: Application = ApplicationDependencies.getApplication()

  private val store: Store<HelpSettingsState> = Store(getCurrentState())

  val state: LiveData<HelpSettingsState> = store.stateLiveData

  fun setUpdateApkEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkEnabled(application, enabled)
    NotificationChannels.create(application)
    if (enabled) {
      checkForUpdates()
    }
    refreshState()
  }

  fun setIncludeBetaEnabled(enabled: Boolean) {
    TextSecurePreferences.setUpdateApkIncludeBetaEnabled(application, enabled)
    UpdateApkReadyListener.clearNotification(application)
    checkForUpdates()
    refreshState()
  }

  private fun checkForUpdates() {
    UpdateApkRefreshListener.schedule(application)
    ApplicationDependencies.getJobManager().add(UpdateApkJob())
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
    )
  }
}
