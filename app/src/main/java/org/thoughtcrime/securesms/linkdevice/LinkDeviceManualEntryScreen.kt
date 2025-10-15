package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult

@Composable
fun LinkDeviceManualEntryScreen(
  onLinkNewDeviceWithUrl: (String) -> Unit,
  linkDeviceResult: LinkDeviceResult,
  onLinkDeviceSuccess: () -> Unit,
  onLinkDeviceFailure: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  LaunchedEffect(linkDeviceResult) {
    when (linkDeviceResult) {
      is LinkDeviceResult.Success -> onLinkDeviceSuccess()
      is LinkDeviceResult.NoDevice -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_no_device, onLinkDeviceFailure)
      is LinkDeviceResult.NetworkError -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_network_error, onLinkDeviceFailure)
      is LinkDeviceResult.KeyError -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_key_error, onLinkDeviceFailure)
      is LinkDeviceResult.LimitExceeded -> makeToast(context, R.string.DeviceProvisioningActivity_sorry_you_have_too_many_devices_linked_already, onLinkDeviceFailure)
      is LinkDeviceResult.BadCode -> makeToast(context, R.string.DeviceActivity_sorry_this_is_not_a_valid_device_link_qr_code, onLinkDeviceFailure)
      is LinkDeviceResult.None -> Unit
    }
  }

  var qrLink by remember { mutableStateOf("") }
  var isValid by remember { mutableStateOf(false) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight()
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 24.dp)
    ) {
      item {
        Text(
          text = stringResource(id = R.string.enter_device_link_dialog__if_your_phone_cant_scan_the_qr_code_you_can_manually_enter_the_link_encoded_in_the_qr_code),
          style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          modifier = Modifier.padding(vertical = 12.dp)
        )
      }

      item {
        val textFieldStyle = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp
        )
        TextField(
          value = qrLink,
          onValueChange = {
            isValid = LinkDeviceRepository.isValidQr(Uri.parse(it))
            qrLink = it
          },
          isError = !isValid,
          placeholder = {
            Text(
              text = stringResource(id = R.string.enter_device_link_dialog__url),
              style = textFieldStyle
            )
          },
          keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done
          ),
          textStyle = textFieldStyle,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .defaultMinSize(minHeight = 40.dp)
        )
      }

      item {
        Text(
          text = stringResource(id = R.string.AddLinkDeviceFragment__this_device_will_see_your_groups_contacts),
          style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          modifier = Modifier.padding(vertical = 12.dp)
        )
      }
    }

    Row {
      Buttons.LargeTonal(
        enabled = isValid,
        onClick = { onLinkNewDeviceWithUrl(qrLink) },
        modifier = Modifier
          .defaultMinSize(minWidth = 220.dp)
          .padding(vertical = 16.dp)
      ) {
        Text(text = stringResource(id = R.string.device_list_fragment__link_new_device))
      }
    }
  }
}

private fun makeToast(context: Context, messageId: Int, onLinkDeviceFailure: () -> Unit) {
  Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
  onLinkDeviceFailure()
}

@DayNightPreviews
@Composable
private fun LinkDeviceManualEntryScreenPreview() {
  Previews.Preview {
    LinkDeviceManualEntryScreen(
      onLinkNewDeviceWithUrl = {},
      linkDeviceResult = LinkDeviceResult.None,
      onLinkDeviceSuccess = {},
      onLinkDeviceFailure = {},
    )
  }
}
