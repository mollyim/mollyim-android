package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Bottom sheet dialog displayed when users click 'Link a device'
 */
class LinkDeviceIntroBottomSheet : ComposeBottomSheetDialogFragment() {

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  override val peekHeightPercentage: Float = 0.8f

  @Composable
  override fun SheetContent() {
    val navController: NavController by remember { mutableStateOf(findNavController()) }

    EducationSheet(
      onClick = { shouldScanQrCode ->
        viewModel.requestLinkWithoutQrCode(!shouldScanQrCode)
        navController.safeNavigate(R.id.action_linkDeviceIntroBottomSheet_to_addLinkDeviceFragment)
      }
    )
  }
}

@Composable
fun EducationSheet(onClick: (Boolean) -> Unit) {
  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.linking_device))

  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp)
  ) {
    BottomSheets.Handle()
    Box(modifier = Modifier.size(150.dp)) {
      LottieAnimation(composition, iterations = LottieConstants.IterateForever, modifier = Modifier.matchParentSize())
    }
    Text(
      text = stringResource(R.string.LinkDeviceFragment__link_a_new_device),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = 12.dp)
    )
    Text(
      text = stringResource(R.string.AddLinkDeviceFragment__use_this_device_to_scan_qr_code),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 12.dp)
    )
    Buttons.LargeTonal(
      onClick = { onClick(true) },
      modifier = Modifier.defaultMinSize(minWidth = 220.dp)
    ) {
      Text(stringResource(id = R.string.AddLinkDeviceFragment__scan_qr_code))
    }
    Buttons.Small(
      onClick = { onClick(false) },
      modifier = Modifier.defaultMinSize(minWidth = 220.dp)
    ) {
      Text(stringResource(id = R.string.DeviceAddFragment__link_without_scanning))
    }
  }
}

@SignalPreview
@Composable
fun EducationSheetPreview() {
  Previews.BottomSheetPreview {
    EducationSheet(onClick = {})
  }
}
