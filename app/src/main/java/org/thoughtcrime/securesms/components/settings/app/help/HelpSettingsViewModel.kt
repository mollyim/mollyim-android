package org.thoughtcrime.securesms.components.settings.app.help

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.UpdateApkJob
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class HelpSettingsViewModel : ViewModel() {

  private val store: Store<HelpSettingsState> = Store(getCurrentState())

  val state: LiveData<HelpSettingsState> = store.stateLiveData

  fun setUpdateApkEnabled(enabled: Boolean) {
    val context: Context = ApplicationDependencies.getApplication()

    TextSecurePreferences.setUpdateApkEnabled(context, enabled)
    NotificationChannels.create(context)
    if (enabled) {
      UpdateApkRefreshListener.schedule(context)
      ApplicationDependencies.getJobManager().add(UpdateApkJob())
    }
    refreshState()
  }

  fun setLogEnabled(enabled: Boolean) {
    TextSecurePreferences.setLogEnabled(ApplicationDependencies.getApplication(), enabled)
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
      updateApkEnabled = TextSecurePreferences.isUpdateApkEnabled(
        ApplicationDependencies.getApplication()
      ),
      logEnabled = TextSecurePreferences.isLogEnabled(
        ApplicationDependencies.getApplication()
      ),
    )
  }
}
