/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeFullScreenDialogFragment

/**
 * Displayed via the [org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsFragment] whenever the user
 * attempts to paste their recovery key into the input field.
 *
 * A result is always delivered to [REQUEST_KEY] when this fragment is dismissed, with the boolean
 * indicating whether the user chose to proceed with the paste. The host can rely on this firing for
 * every dismissal path (paste, decline, or cancel) to restore its own state.
 */
class RecoveryKeyPasteWarningFragment : ComposeFullScreenDialogFragment() {

  companion object {
    const val REQUEST_KEY = "recovery_key_request"
  }

  private var shouldPaste = false

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return super.onCreateDialog(savedInstanceState).apply {
      window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      window?.setWindowAnimations(0)
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    setFragmentResult(
      REQUEST_KEY,
      Bundle().apply {
        putBoolean(REQUEST_KEY, shouldPaste)
      }
    )

    super.onDismiss(dialog)
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun DialogContent() {
    var isDisplayingFinalWarningDialog by remember { mutableStateOf(false) }

    val eventHandler: (RecoveryKeyWarningSheetEvent) -> Unit = {
      when (it) {
        RecoveryKeyWarningSheetEvent.DoNotShareClick -> {
          dismissAllowingStateLoss()
        }

        RecoveryKeyWarningSheetEvent.GotItClick -> error("Not supported for paste")
        RecoveryKeyWarningSheetEvent.LearnMoreClick -> error("Not supported for paste")
        RecoveryKeyWarningSheetEvent.PasteKeyClick -> {
          shouldPaste = true
          dismissAllowingStateLoss()
        }

        RecoveryKeyWarningSheetEvent.ShareKeyClick -> {
          isDisplayingFinalWarningDialog = true
        }
      }
    }

    if (isDisplayingFinalWarningDialog) {
      RecoveryKeyWarningDialog(
        events = eventHandler
      )
    } else {
      ModalBottomSheet(
        onDismissRequest = { dismissAllowingStateLoss() },
        dragHandle = { BottomSheets.Handle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ) {
        RecoveryKeyWarningSheetContent(
          clipStage = ClipStage.PASTE,
          events = eventHandler
        )
      }
    }
  }
}
