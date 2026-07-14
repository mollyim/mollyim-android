/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * ViewModel for the registration lock PIN entry screen.
 *
 * This screen is shown when the user attempts to register and their account is protected by a registration lock (PIN).
 * The user must enter their PIN to proceed with registration.
 */
class PinEntryForRegistrationLockViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val timeRemaining: Long,
  private val svrCredentials: NetworkController.SvrCredentials
) : EventDrivenViewModel<PinEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(PinEntryForRegistrationLockViewModel::class)
  }

  private val _state = MutableStateFlow(
    PinEntryState(
      mode = PinEntryState.Mode.RegistrationLock
    )
  )

  val state: StateFlow<PinEntryState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PinEntryScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: PinEntryState, event: PinEntryScreenEvents, parentEventEmitter: (RegistrationFlowEvent) -> Unit, stateEmitter: (PinEntryState) -> Unit) {
    when (event) {
      is PinEntryScreenEvents.PinEntered -> {
        val localState = state.copy(loading = true)
        stateEmitter(localState)
        stateEmitter(applyPinEntered(localState, event, parentEventEmitter))
      }
      is PinEntryScreenEvents.Skip -> {
        throw NotImplementedError("Skip is not a valid action during registration lock PIN entry")
      }
      is PinEntryScreenEvents.CreateNewPin,
      is PinEntryScreenEvents.ContactSupport,
      is PinEntryScreenEvents.ParentStateChanged -> Unit
      is PinEntryScreenEvents.ToggleKeyboard -> {
        stateEmitter(PinEntryScreenEventHandler.applyEvent(state, event))
      }
    }
  }

  private suspend fun applyPinEntered(state: PinEntryState, event: PinEntryScreenEvents.PinEntered, parentEventEmitter: (RegistrationFlowEvent) -> Unit): PinEntryState {
    Log.d(TAG, "[PinEntered] Attempting to restore master key from SVR...")

    val restoreResult = repository.restoreMasterKeyFromSvr(svrCredentials, event.pin, forRegistrationLock = true)

    val masterKey: MasterKey = when (restoreResult) {
      is RequestResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully restored master key from SVR.")
        restoreResult.result.masterKey
      }
      is RequestResult.NonSuccess -> {
        return when (val error = restoreResult.error) {
          is NetworkController.RestoreMasterKeyError.WrongPin -> {
            Log.w(TAG, "[PinEntered] Wrong PIN. Tries remaining: ${error.triesRemaining}")
            if (error.triesRemaining <= 0) {
              Log.w(TAG, "[PinEntered] Out of PIN attempts. Account is locked.")
              parentEventEmitter.navigateTo(RegistrationRoute.AccountLocked(timeRemainingMs = timeRemaining))
              state
            } else {
              state.copy(loading = false, triesRemaining = error.triesRemaining)
            }
          }
          is NetworkController.RestoreMasterKeyError.NoDataFound -> {
            Log.w(TAG, "[PinEntered] No SVR data found. Account is locked.")
            parentEventEmitter.navigateTo(RegistrationRoute.AccountLocked(timeRemainingMs = timeRemaining))
            state
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when restoring master key.", restoreResult.networkError)
        return state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when restoring master key.", restoreResult.cause)
        return state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }

    parentEventEmitter(RegistrationFlowEvent.MasterKeyRestoredFromSvr(masterKey))

    val registrationLockToken = masterKey.deriveRegistrationLock()

    val e164 = parentState.value.sessionE164
    val sessionId = parentState.value.sessionMetadata?.id

    if (e164 == null) {
      Log.w(TAG, "[PinEntered] Missing e164. Resetting state.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val registerResult = if (sessionId != null) {
      Log.d(TAG, "[PinEntered] Attempting to register with the session and registration lock token...")
      repository.registerAccountWithSession(
        e164 = e164,
        sessionId = sessionId,
        registrationLock = registrationLockToken,
        skipDeviceTransfer = true
      )
    } else {
      Log.d(TAG, "[PinEntered] No session present. Attempting to register with the RRP from the restored master key and the registration lock token...")
      repository.registerAccountWithRecoveryPassword(
        e164 = e164,
        recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
        registrationLock = registrationLockToken,
        skipDeviceTransfer = true
      )
    }

    return when (registerResult) {
      is RequestResult.Success -> {
        Log.i(TAG, "[PinEntered] Successfully registered!")
        val (response, keyMaterial) = registerResult.result
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))
        repository.enqueueSvrResetGuessCountJob()
        repository.restoreAccountRecord()
        when {
          response.reregistration && parentState.value.pendingRestoreOption == null -> parentEventEmitter.navigateTo(RegistrationRoute.ArchiveRestoreSelection.forPostRegisterWithPinKnown())
          else -> parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
        }
        state
      }
      is RequestResult.NonSuccess -> {
        when (val error = registerResult.error) {
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[PinEntered] Session not found or verified: ${error.message}. Resetting.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[PinEntered] Still getting registration lock error after providing token. This shouldn't happen. Resetting state.")
            parentEventEmitter(RegistrationFlowEvent.ResetState)
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[PinEntered] Rate limited when registering. Retry After: ${error.retryAfter}")
            state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.RateLimited(error.retryAfter))
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[PinEntered] Invalid request when registering: ${error.message}")
            state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            Log.w(TAG, "[PinEntered] Device transfer possible. This shouldn't happen when skipDeviceTransfer is true.")
            state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[PinEntered] Registration recovery password incorrect: ${error.message}. Marking recovery password invalid and navigating back.")
            parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
            parentEventEmitter.navigateBack()
            state
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[PinEntered] Network error when registering.", registerResult.networkError)
        state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.NetworkError)
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[PinEntered] Application error when registering.", registerResult.cause)
        state.copy(loading = false, oneTimeEvent = PinEntryState.OneTimeEvent.UnknownError)
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val timeRemaining: Long,
    private val svrCredentials: NetworkController.SvrCredentials
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return PinEntryForRegistrationLockViewModel(
        repository,
        parentState,
        parentEventEmitter,
        timeRemaining,
        svrCredentials
      ) as T
    }
  }
}
