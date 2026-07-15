/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

sealed class ArchiveRestoreSelectionScreenEvents {
  data class RestoreOptionSelected(val option: ArchiveRestoreOption) : ArchiveRestoreSelectionScreenEvents()

  data object ConfirmSkip : ArchiveRestoreSelectionScreenEvents()

  data object DismissSkipWarning : ArchiveRestoreSelectionScreenEvents()
}
