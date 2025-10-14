package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Bottom sheet dialog prompting users to name their newly linked device
 */
class LinkDeviceFinishedSheet : ComposeBottomSheetDialogFragment() {

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  override fun onStart() {
    super.onStart()
    viewModel.onBottomSheetVisible()
  }

  @Composable
  override fun SheetContent() {
    FinishedSheet {
      viewModel.onBottomSheetDismissed()
      this.dismissAllowingStateLoss()
    }
  }
}

@Composable
fun FinishedSheet(onClick: () -> Unit) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentSize(Alignment.Center)
      .padding(16.dp)
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.ic_devices),
      contentDescription = null,
      tint = Color.Unspecified
    )
    Text(
      text = stringResource(R.string.AddLinkDeviceFragment__finish_linking_on_other_device),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
    )
    Text(
      text = stringResource(R.string.AddLinkDeviceFragment__finish_linking_signal),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(horizontal = 12.dp)
    )
    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp).padding(vertical = 20.dp, horizontal = 12.dp)
    ) {
      Text(stringResource(id = R.string.AddLinkDeviceFragment__okay))
    }
  }
}

@DayNightPreviews
@Composable
fun FinishedSheetSheetPreview() {
  Previews.BottomSheetPreview {
    FinishedSheet(onClick = {})
  }
}
