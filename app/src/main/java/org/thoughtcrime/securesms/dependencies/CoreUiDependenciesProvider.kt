/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.core.ui.CoreUiDependencies
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.BackupUtil

object CoreUiDependenciesProvider : CoreUiDependencies.Provider {
  override fun provideBackupBaseDirName(): String {
    return BackupUtil.getBackupBaseDirName();
  }

  override fun provideIsIncognitoKeyboardEnabled(): Boolean {
    return SignalStore.settings.isIncognitoKeyboardEnabled
  }

  override fun provideIsScreenSecurityEnabled(): Boolean {
    if (ScreenLockController.alwaysSetSecureFlagOnResume || KeyCachingService.isLocked()) {
      return true
    }
    return SignalStore.settings.isScreenSecurityEnabled
  }
}
