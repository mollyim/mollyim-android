package org.thoughtcrime.securesms.components.settings.app

import androidx.compose.runtime.Immutable
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

@Immutable
data class AppSettingsState(
  val unreadPaymentsCount: Int,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean,
  val showInternalPreferences: Boolean = RemoteConfig.internalUser,
  val showAppUpdates: Boolean = BuildConfig.MANAGES_MOLLY_UPDATES,
  val showBackups: Boolean = RemoteConfig.messageBackupsInSettings || SignalStore.backup.backupTier != null || SignalStore.backup.latestBackupTier != null,
  val backupFailureState: BackupFailureState = BackupFailureState.NONE,
  val legacyLocalBackupsEnabled: Boolean
) {
  fun isRegisteredAndUpToDate(): Boolean {
    return !userUnregistered && !clientDeprecated
  }
}
