package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil

/**
 *  Bottom sheet dialog displayed when users click 'Learn more' when linking a device
 */
class LinkDeviceLearnMoreBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.8f

  @Composable
  override fun SheetContent() {
    LearnMoreSheet()
  }
}

@Composable
fun LearnMoreSheet() {
  val context = LocalContext.current
  val downloadUrl = stringResource(R.string.install_url)
  val linkText = downloadUrl.removePrefix("https://")
  val fullString = stringResource(id = R.string.LinkDeviceFragment__for_android_devices_visit_s_to_install_molly, linkText)
  val spanned = SpanUtil.urlSubsequence(fullString, linkText, downloadUrl)

  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentSize(Alignment.Center)
      .padding(bottom = 48.dp)
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.ic_all_devices),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.size(110.dp)
    )
    Text(
      style = MaterialTheme.typography.titleLarge,
      text = stringResource(R.string.LinkDeviceFragment__molly_on_another_device),
      modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
    LinkedDeviceInformationRow(
      painterResource(R.drawable.symbol_lock_24),
      stringResource(R.string.LinkDeviceFragment__messaging_is_private_on_all_linked_devices)
    )
    LinkedDeviceInformationRow(
      painterResource(R.drawable.ic_replies_outline_20),
      stringResource(R.string.LinkDeviceFragment__once_linked_new_messages_sync_across_devices)
    )
    LinkedDeviceInformationRow(
      painterResource(R.drawable.symbol_devices_24),
      stringResource(R.string.LinkDeviceFragment__molly_supports_linking_to_other_android_devices)
    )
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 40.dp, end = 32.dp)) {
      Icon(
        painter = painterResource(R.drawable.symbol_save_android_24),
        contentDescription = stringResource(R.string.preferences__linked_devices),
        modifier = Modifier.size(24.dp).padding(top = 4.dp)
      )
      Texts.LinkifiedText(
        textWithUrlSpans = spanned,
        onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.padding(start = 20.dp)
      )
    }
  }
}

@Composable
private fun LinkedDeviceInformationRow(
  iconPainter: Painter,
  text: String
) {
  Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 40.dp, end = 32.dp)) {
    Icon(
      painter = iconPainter,
      contentDescription = null,
      modifier = Modifier.size(24.dp).padding(top = 4.dp)
    )
    Text(
      style = MaterialTheme.typography.bodyLarge,
      text = text,
      modifier = Modifier.padding(start = 20.dp)
    )
  }
}

@DayNightPreviews
@Composable
fun LearnMorePreview() {
  Previews.BottomSheetPreview {
    LearnMoreSheet()
  }
}
