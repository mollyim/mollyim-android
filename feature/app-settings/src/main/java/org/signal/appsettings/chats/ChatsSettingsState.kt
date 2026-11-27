/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import org.signal.appsettings.backups.BackupCreationProgress

data class ChatsSettingsState(
  val generateLinkPreviews: Boolean,
  val useAddressBook: Boolean,
  val keepMutedChatsArchived: Boolean,
  val useSystemEmoji: Boolean,
  val enterKeySends: Boolean,
  val localBackupsEnabled: Boolean,
  val folderCount: Int,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean,
  val isPlaintextExportEnabled: Boolean,
  val plaintextExportProgress: BackupCreationProgress = BackupCreationProgress.Idle,
  val chatExportState: ChatExportState = ChatExportState.None,
  val includeMediaInExport: Boolean = false,
  val shouldAutoplayStickersAndGifs: Boolean = false
  // JW: added extra preferences
  ,
  val keepViewOnceMessages: Boolean = false,
  val ignoreRemoteDelete: Boolean = false,
  val ignoreAdminDelete: Boolean = false,
  val deleteMediaOnly: Boolean = false,
  //val googleMapType: String = "normal",
  val whoCanAddYouToGroups: String = "nonblocked",
  val generateLinkPreviewImages: Boolean = true,
) {
  fun isRegisteredAndUpToDate(): Boolean {
    return !userUnregistered && !clientDeprecated
  }
}
