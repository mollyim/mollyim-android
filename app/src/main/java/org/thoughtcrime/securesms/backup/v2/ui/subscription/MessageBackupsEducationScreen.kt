/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.BetaHeader
import org.thoughtcrime.securesms.components.compose.TextWithBetaLabel
import org.signal.core.ui.R as CoreUiR

/**
 * Educational content which allows user to proceed to set up automatic backups
 * or navigate to a support page to learn more.
 */
@Composable
fun MessageBackupsEducationScreen(
  onNavigationClick: () -> Unit,
  onEnableBackups: () -> Unit,
  onLearnMore: () -> Unit
) {
  Scaffolds.Settings(
    onNavigationClick = onNavigationClick,
    navigationContentDescription = stringResource(android.R.string.cancel),
    navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_x_24),
    title = ""
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
        .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
    ) {
      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        item {
          BetaHeader()
        }

        item {
          Image(
            painter = painterResource(id = R.drawable.image_signal_backups),
            contentDescription = null,
            modifier = Modifier
              .padding(top = 24.dp)
              .size(80.dp)
          )
        }

        item {
          TextWithBetaLabel(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__signal_backups),
            textStyle = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 15.dp)
          )
        }

        item {
          Text(
            text = stringResource(id = R.string.MessageBackupsEducationScreen__backup_your_messages_and_media),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
          )
        }

        item {
          Column(
            modifier = Modifier.padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
            NotableFeatureRow(
              painter = painterResource(id = R.drawable.symbol_lock_compact_20),
              text = stringResource(id = R.string.MessageBackupsEducationScreen__end_to_end_encrypted)
            )

            NotableFeatureRow(
              painter = painterResource(id = R.drawable.symbol_check_square_compact_20),
              text = stringResource(id = R.string.MessageBackupsEducationScreen__optional_always)
            )

            NotableFeatureRow(
              painter = painterResource(id = R.drawable.symbol_trash_compact_20),
              text = stringResource(id = R.string.MessageBackupsEducationScreen__delete_your_backup_anytime)
            )
          }
        }
      }

      Buttons.LargeTonal(
        onClick = onEnableBackups,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = stringResource(id = R.string.MessageBackupsEducationScreen__enable_backups)
        )
      }

      TextButton(
        onClick = onLearnMore,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
      ) {
        Text(
          text = stringResource(id = R.string.MessageBackupsEducationScreen__learn_more)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun MessageBackupsEducationSheetPreview() {
  Previews.Preview {
    MessageBackupsEducationScreen(
      onNavigationClick = {},
      onEnableBackups = {},
      onLearnMore = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun NotableFeatureRowPreview() {
  Previews.Preview {
    NotableFeatureRow(
      painter = painterResource(id = R.drawable.symbol_lock_compact_20),
      text = "Notable feature information"
    )
  }
}

@Composable
private fun NotableFeatureRow(
  painter: Painter,
  text: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painter,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(end = 8.dp)
        .size(32.dp)
        .padding(6.dp)
    )

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
