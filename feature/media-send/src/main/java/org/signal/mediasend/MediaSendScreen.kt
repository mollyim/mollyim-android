/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.signal.core.ui.compose.theme.SignalTheme

@Composable
fun MediaSendScreen(
  contractArgs: MediaSendActivityContract.Args,
  modifier: Modifier = Modifier,
  textStoryEditorSlot: @Composable () -> Unit = {},
  sendSlot: @Composable (MediaSendState) -> Unit = {},
  onExternalHudCommand: (HudCommand) -> Unit = {}
) {
  val viewModel = viewModel<MediaSendViewModel>(factory = MediaSendViewModel.Factory(args = contractArgs))

  LaunchedEffect(viewModel) {
    viewModel.hudCommands.collect { command ->
      onExternalHudCommand(command)
    }
  }

  SignalTheme {
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides LocalActivity.current as NavigationEventDispatcherOwner) {
      Surface {
        MediaSendNavDisplay(
          stateFlow = viewModel.state,
          snackbarEvents = viewModel.snackbarEvents,
          backStack = viewModel.backStack,
          eventHandler = viewModel,
          modifier = modifier,
          textStoryEditorSlot = textStoryEditorSlot,
          sendSlot = sendSlot
        )
      }
    }
  }
}
