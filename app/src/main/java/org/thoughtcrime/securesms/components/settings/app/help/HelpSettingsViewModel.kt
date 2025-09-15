package org.thoughtcrime.securesms.components.settings.app.help

import android.app.Application
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.TextSecurePreferences

class HelpSettingsViewModel : ViewModel() {

  private val application: Application = AppDependencies.application

  private val internalState = MutableStateFlow(getState())

  val state: StateFlow<HelpSettingsState> = internalState

  fun setLogEnabled(enabled: Boolean) {
    TextSecurePreferences.setLogEnabled(application, enabled)
    Log.setLogging(enabled)
    if (!enabled) {
      Log.wipeLogs()
    }
    refresh()
  }

  fun refresh() {
    internalState.update { getState() }
  }

  private fun getState(): HelpSettingsState {
    return HelpSettingsState(
      logEnabled = TextSecurePreferences.isLogEnabled(application),
    )
  }
}
