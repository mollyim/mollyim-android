package org.thoughtcrime.securesms.components.settings.app.help

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class HelpSettingsViewModel : ViewModel() {

  private val store: Store<HelpSettingsState> = Store(getCurrentState())

  val state: LiveData<HelpSettingsState> = store.stateLiveData

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
