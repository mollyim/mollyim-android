/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.compose.Buttons

/**
 * Screen that allows user to capture the media they will send using a camera or text story
 */
@Composable
fun MediaCaptureScreen(
  backStack: NavBackStack<NavKey>,
  onEvent: (MediaCaptureScreenEvent) -> Unit,
  cameraSlot: @Composable () -> Unit,
  textStoryEditorSlot: @Composable () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    when (backStack.last()) {
      is MediaSendNavKey.Capture.TextStory -> textStoryEditorSlot()
      else -> cameraSlot()
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
    ) {
      Buttons.Small(onClick = { onEvent(MediaCaptureScreenEvent.ShowCamera) }) {
        Text(text = stringResource(R.string.MediaCaptureScreen__camera))
      }

      Buttons.Small(onClick = { onEvent(MediaCaptureScreenEvent.ShowTextStory) }) {
        Text(text = stringResource(R.string.MediaCaptureScreen__text_story))
      }
    }
  }
}
