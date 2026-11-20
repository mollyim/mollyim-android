package org.thoughtcrime.securesms.components.settings.app.privacy

import kotlinx.coroutines.withContext
import org.signal.appsettings.privacy.PassphraseLockTriggerOption
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * All of the storage access behind [PrivacySettingsViewModel].
 */
object PrivacySettingsRepository {

  suspend fun getBlockedCount(): Int {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.getBlocked().size
    }
  }

  fun hasPhoneNumber(): Boolean = !SignalStore.account.isPhoneNumberless

  fun isBlockUnknownEnabled(): Boolean = SignalStore.molly.blockUnknownEnabled

  fun setBlockUnknownEnabled(enabled: Boolean) {
    SignalStore.molly.blockUnknownEnabled = enabled
  }

  fun isReadReceiptsEnabled(): Boolean = SignalStore.settings.isReadReceiptsEnabled

  suspend fun setReadReceiptsEnabled(enabled: Boolean) {
    SignalStore.settings.isReadReceiptsEnabled = enabled
    syncConfiguration()
  }

  fun isTypingIndicatorsEnabled(): Boolean = SignalStore.settings.isTypingIndicatorsEnabled

  suspend fun setTypingIndicatorsEnabled(enabled: Boolean) {
    SignalStore.settings.isTypingIndicatorsEnabled = enabled
    syncConfiguration()

    if (!enabled) {
      AppDependencies.typingStatusRepository.clear()
    }
  }

  fun getUniversalExpireTimer(): Duration = SignalStore.settings.universalExpireTimer.seconds

  fun isPassphraseLockEnabled(): Boolean = PlainTextKeyValueStore.passphraseLockEnabled

  fun setPassphraseLockEnabled(enabled: Boolean) {
    PlainTextKeyValueStore.passphraseLockEnabled = enabled
  }

  fun getPassphraseLockTriggers(): Set<String> = SignalStore.molly.passphraseLockTrigger.triggers

  fun isPassphraseLockTimeoutEnabled(): Boolean = SignalStore.molly.passphraseLockTrigger.isTimeoutEnabled

  fun setPassphraseLockTriggers(triggers: Set<String>) {
    SignalStore.molly.passphraseLockTrigger = PassphraseLockTriggerPreference(triggers)
  }

  fun getPassphraseLockTimeout(): Duration = SignalStore.molly.passphraseLockTimeout.seconds

  fun setPassphraseLockTimeout(seconds: Long) {
    SignalStore.molly.passphraseLockTimeout = seconds
  }

  fun getPassphraseLockTriggerOptions(): List<PassphraseLockTriggerOption> {
    val resources = AppDependencies.application.resources
    val keys = resources.getStringArray(R.array.pref_passphrase_lock_trigger_entries)
    val labels = resources.getStringArray(R.array.pref_passphrase_lock_trigger_values)
    return keys.indices.map { PassphraseLockTriggerOption(keys[it], labels[it]) }
  }

  fun isBiometricScreenLockEnabled(): Boolean = PlainTextKeyValueStore.biometricScreenLockEnabled

  fun setBiometricScreenLockEnabled(enabled: Boolean) {
    PlainTextKeyValueStore.biometricScreenLockEnabled = enabled
    ScreenLockController.enableAutoLock(enabled)
    ScreenLockController.lockScreenAtStart = false
  }

  fun isScreenSecurityForced(): Boolean = ScreenLockController.alwaysSetSecureFlagOnResume

  fun isScreenSecurityEnabled(): Boolean = SignalStore.settings.isScreenSecurityEnabled

  fun setScreenSecurityEnabled(enabled: Boolean) {
    SignalStore.settings.isScreenSecurityEnabled = enabled
  }

  fun isIncognitoKeyboardEnabled(): Boolean = SignalStore.settings.isIncognitoKeyboardEnabled

  fun setIncognitoKeyboardEnabled(enabled: Boolean) {
    SignalStore.settings.isIncognitoKeyboardEnabled = enabled
  }

  private suspend fun syncConfiguration() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          SignalStore.settings.isReadReceiptsEnabled,
          SignalStore.settings.isTypingIndicatorsEnabled,
          SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled,
          SignalStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }
}
