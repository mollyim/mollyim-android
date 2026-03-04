/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.compose.SignalTheme

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun LinkProvisioningQrSection(
  qrState: RegisterLinkDeviceQrViewModel.QrState,
  onRetry: () -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(space = 48.dp, alignment = Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(space = 48.dp),
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    Box(
      modifier = Modifier
        .widthIn(160.dp, 320.dp)
        .aspectRatio(1f)
        .clip(RoundedCornerShape(24.dp))
        .background(SignalTheme.colors.colorSurface5)
        .padding(40.dp)
    ) {
      SignalTheme(isDarkMode = false) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          AnimatedContent(
            targetState = qrState,
            contentKey = { it::class },
            contentAlignment = Alignment.Center,
            label = "qr-code-progress",
            modifier = Modifier
              .fillMaxWidth()
              .fillMaxHeight()
          ) { currentQrState ->
            when (currentQrState) {
              is RegisterLinkDeviceQrViewModel.QrState.Loaded -> {
                QrCode(
                  data = currentQrState.qrData,
                  foregroundColor = Color(0xFF2449C0),
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                )
              }

              RegisterLinkDeviceQrViewModel.QrState.Loading -> {
                Box(contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
              }

              is RegisterLinkDeviceQrViewModel.QrState.Scanned,
              RegisterLinkDeviceQrViewModel.QrState.Failed -> {
                Column(
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  val text = if (currentQrState is RegisterLinkDeviceQrViewModel.QrState.Scanned) {
                    "Scanned on device"
                  } else {
                    stringResource(R.string.RestoreViaQr_qr_code_error)
                  }

                  Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  Spacer(modifier = Modifier.height(8.dp))

                  Buttons.Small(onClick = onRetry) {
                    Text(text = stringResource(R.string.RestoreViaQr_retry))
                  }
                }
              }
            }
          }
        }
      }
    }

    // TODO [link-device] use actual copy
    Column(
      modifier = Modifier.align(alignment = Alignment.CenterVertically)
        .widthIn(160.dp, 320.dp)
    ) {
      InstructionRow(
        icon = SignalIcons.Settings.painter,
        instruction = "Open Signal Settings on your device"
      )

      InstructionRow(
        icon = SignalIcons.Link.painter,
        instruction = "Tap \"Linked devices\""
      )

      InstructionRow(
        icon = SignalIcons.QrCode.painter,
        instruction = "Tap \"Link a new device\" and scan this code"
      )
    }
  }
}

@Composable
private fun InstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    modifier = Modifier
      .padding(vertical = 12.dp)
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}


