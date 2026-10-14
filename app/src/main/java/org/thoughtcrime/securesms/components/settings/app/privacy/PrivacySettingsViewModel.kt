package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.screenlock.ScreenLockMode
import org.thoughtcrime.securesms.screenlock.ScreenLockPinStore
import org.thoughtcrime.securesms.screenlock.ScreenLockPreferences
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: PrivacySettingsRepository
) : ViewModel() {

  private val application: Application = AppDependencies.application

  private val store = Store(getState())

  val state: LiveData<PrivacySettingsState> = store.stateLiveData

  fun refreshBlockedCount() {
    repository.getBlockedCount { count ->
      store.update { it.copy(blockedCount = count) }
      refresh()
    }
  }

  fun setBlockUnknownEnabled(enabled: Boolean) {
    TextSecurePreferences.setBlockUnknownEnabled(application, enabled)
    refresh()
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.READ_RECEIPTS_PREF, enabled).apply()
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.TYPING_INDICATORS, enabled).apply()
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setPassphraseLockEnabled(enabled: Boolean) {
    TextSecurePreferences.setPassphraseLockEnabled(application, enabled)
    refresh()
  }

  fun setPassphraseLockTrigger(resultSet: Set<String>) {
    sharedPreferences.edit().putStringSet(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER, resultSet).apply()
    refresh()
  }

  fun setPassphraseLockTimeout(seconds: Long) {
    sharedPreferences.edit().putLong(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT, seconds).apply()
    refresh()
  }

  fun setScreenLockMode(mode: ScreenLockMode, generation: Long): Boolean {
    return try {
      if (!ScreenLockPinStore.changeMode(application, mode, generation)) return false
      refresh()
      true
    } catch (_: Exception) { false }
  }

  fun setScreenLockTimeout(seconds: Long) {
    ScreenLockPreferences.setTimeoutSeconds(application, seconds)
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, enabled).apply()
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBOARD_PREF, enabled).apply()
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      blockedCount = 0,
      blockUnknown = TextSecurePreferences.isBlockUnknownEnabled(application),
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(application),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(application),
      passphraseLock = TextSecurePreferences.isPassphraseLockEnabled(application),
      passphraseLockTriggerValues = TextSecurePreferences.getPassphraseLockTrigger(application).triggers,
      passphraseLockTimeout = TextSecurePreferences.getPassphraseLockTimeout(application),
      screenLockMode = ScreenLockPreferences.getMode(application),
      screenLockTimeout = ScreenLockPreferences.getTimeoutSeconds(application),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(application),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(application),
      universalExpireTimer = SignalStore.settings.universalExpireTimer
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(sharedPreferences, repository)))
    }
  }
}
