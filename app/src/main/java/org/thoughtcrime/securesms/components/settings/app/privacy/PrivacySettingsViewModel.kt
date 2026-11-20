package org.thoughtcrime.securesms.components.settings.app.privacy

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.privacy.PassphraseDialogMode
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsState
import org.signal.appsettings.privacy.PrivacySettingsState.Dialog
import org.signal.appsettings.privacy.PrivacySettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.privacy.PrivacySettingsScreen]. Navigation goes straight to [parentEventEmitter].
 */
class PrivacySettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<PrivacySettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(PrivacySettingsViewModel::class)
  }

  private val _state = MutableStateFlow(PrivacySettingsState())
  private val _actions = Channel<PrivacySettingsAction>(Channel.BUFFERED)

  val state: StateFlow<PrivacySettingsState> = _state.asStateFlow()
  val actions: Flow<PrivacySettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()
  }

  override suspend fun processEvent(event: PrivacySettingsEvent) {
    when (event) {
      PrivacySettingsEvent.ScreenResumed -> {
        refresh()
        _state.update { it.copy(blockedCount = PrivacySettingsRepository.getBlockedCount()) }
      }

      PrivacySettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()

      PrivacySettingsEvent.PhoneNumberClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy)

      PrivacySettingsEvent.BlockedClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.BlockedUsers)

      is PrivacySettingsEvent.ReadReceiptsToggled -> {
        PrivacySettingsRepository.setReadReceiptsEnabled(event.enabled)
        refresh()
      }

      is PrivacySettingsEvent.TypingIndicatorsToggled -> {
        PrivacySettingsRepository.setTypingIndicatorsEnabled(event.enabled)
        refresh()
      }

      PrivacySettingsEvent.DefaultTimerClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.ExpiringMessages)

      is PrivacySettingsEvent.PassphraseLockToggled -> {
        val mode = if (event.enabled) PassphraseDialogMode.ENABLE else PassphraseDialogMode.DISABLE
        _actions.send(PrivacySettingsAction.LaunchPassphraseDialog(mode))
      }

      PrivacySettingsEvent.ChangePassphraseClicked -> _actions.send(PrivacySettingsAction.LaunchPassphraseDialog(PassphraseDialogMode.CHANGE))

      PrivacySettingsEvent.AutomaticLockdownClicked -> _state.update { it.copy(dialog = Dialog.AutomaticLockdown) }

      is PrivacySettingsEvent.AutomaticLockdownSelected -> {
        if (_state.value.dialog != Dialog.AutomaticLockdown) return
        _state.update { it.copy(dialog = Dialog.None) }
        PrivacySettingsRepository.setPassphraseLockTriggers(event.triggers)
        refresh()
      }

      PrivacySettingsEvent.PassphraseLockTimeoutClicked -> {
        _actions.send(PrivacySettingsAction.LaunchPassphraseLockTimeoutPicker(_state.value.passphraseLockTimeout))
      }

      is PrivacySettingsEvent.PassphraseLockTimeoutSelected -> {
        PrivacySettingsRepository.setPassphraseLockTimeout(event.timeout.inWholeSeconds)
        refresh()
      }

      is PrivacySettingsEvent.BiometricScreenLockToggled -> {
        if (event.enabled) {
          _actions.send(PrivacySettingsAction.EnableBiometricScreenLock)
        } else {
          applyBiometricScreenLock(false)
        }
      }

      PrivacySettingsEvent.BiometricScreenLockEnableAuthenticated -> applyBiometricScreenLock(true)

      PrivacySettingsEvent.BiometricScreenLockUnavailable -> _state.update { it.copy(snackbar = Snackbar.BiometricsUnavailable) }

      PrivacySettingsEvent.BiometricEnrollmentRequired -> _state.update { it.copy(snackbar = Snackbar.BiometricEnrollmentRequired) }

      is PrivacySettingsEvent.BlockUnknownToggled -> {
        PrivacySettingsRepository.setBlockUnknownEnabled(event.enabled)
        refresh()
      }

      is PrivacySettingsEvent.ScreenSecurityToggled -> {
        PrivacySettingsRepository.setScreenSecurityEnabled(event.enabled)
        refresh()
        _actions.send(PrivacySettingsAction.ApplyScreenSecurity(event.enabled))
      }

      is PrivacySettingsEvent.IncognitoKeyboardToggled -> {
        PrivacySettingsRepository.setIncognitoKeyboardEnabled(event.enabled)
        refresh()
      }

      PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked -> _actions.send(PrivacySettingsAction.OpenIncognitoKeyboardLearnMore)

      PrivacySettingsEvent.DeviceSecuritySettingsUnavailable -> _state.update { it.copy(snackbar = Snackbar.DeviceSecuritySettingsUnavailable) }

      PrivacySettingsEvent.AdvancedClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.PrivacyRoute.Advanced)

      PrivacySettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = Dialog.None) }

      PrivacySettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = Snackbar.None) }
    }
  }

  /** Host callback after native enable/disable succeeds. Kept synchronous so key caching observes the new lock state. */
  fun completePassphraseLockChange(enabled: Boolean) {
    PrivacySettingsRepository.setPassphraseLockEnabled(enabled)
    refresh()
  }

  private suspend fun applyBiometricScreenLock(enabled: Boolean) {
    PrivacySettingsRepository.setBiometricScreenLockEnabled(enabled)
    refresh()
    _actions.send(PrivacySettingsAction.ApplyBiometricScreenLock(enabled))
  }

  private fun refresh() {
    _state.update {
      it.copy(
        hasPhoneNumber = PrivacySettingsRepository.hasPhoneNumber(),
        blockUnknown = PrivacySettingsRepository.isBlockUnknownEnabled(),
        readReceipts = PrivacySettingsRepository.isReadReceiptsEnabled(),
        typingIndicators = PrivacySettingsRepository.isTypingIndicatorsEnabled(),
        universalExpireTimer = PrivacySettingsRepository.getUniversalExpireTimer(),
        passphraseLock = PrivacySettingsRepository.isPassphraseLockEnabled(),
        passphraseLockTriggerValues = PrivacySettingsRepository.getPassphraseLockTriggers(),
        passphraseLockTriggerOptions = PrivacySettingsRepository.getPassphraseLockTriggerOptions(),
        isPassphraseLockTimeoutEnabled = PrivacySettingsRepository.isPassphraseLockTimeoutEnabled(),
        passphraseLockTimeout = PrivacySettingsRepository.getPassphraseLockTimeout(),
        biometricScreenLock = PrivacySettingsRepository.isBiometricScreenLockEnabled(),
        screenSecurity = PrivacySettingsRepository.isScreenSecurityEnabled(),
        isScreenSecurityForced = PrivacySettingsRepository.isScreenSecurityForced(),
        incognitoKeyboard = PrivacySettingsRepository.isIncognitoKeyboardEnabled()
      )
    }
  }
}
