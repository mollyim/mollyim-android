package im.molly.unifiedpush.components.settings.app.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.camera.CameraCaptureMode
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenState
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.qr.QrCrosshair
import org.signal.mediasend.R as MediaSendR

/**
 * A screen that allows you to scan a QR code to start a chat.
 */
@Composable
fun MollySocketQrScanScreen(
  qrScanResult: QrScanResult?,
  cameraState: CameraScreenState,
  cameraEmitter: (CameraScreenEvents) -> Unit,
  onQrResultHandled: () -> Unit,
  onOpenCameraClicked: () -> Unit,
  onOpenGalleryClicked: () -> Unit,
  onDataFound: (String) -> Unit,
  hasCameraPermission: Boolean,
  modifier: Modifier = Modifier
) {
  when (qrScanResult) {
    QrScanResult.InvalidData -> {
      QrScanResultDialog(message = stringResource(R.string.UsernameLinkSettings_qr_result_invalid), onDismiss = onQrResultHandled)
    }

    QrScanResult.NetworkError -> {
      QrScanResultDialog(message = stringResource(R.string.MollySocketLink_experienced_a_network_error_please_try_again), onDismiss = onQrResultHandled)
    }

    QrScanResult.QrNotFound -> {
      QrScanResultDialog(
        title = stringResource(R.string.UsernameLinkSettings_qr_code_not_found),
        message = stringResource(R.string.MollySocketLink_try_scanning_another_image_containing_a_mollysocket_qr_code),
        onDismiss = onQrResultHandled
      )
    }

    is QrScanResult.NotFound -> {
      QrScanResultDialog(message = stringResource(R.string.MollySocketLink_mollysocket_server_not_found_at_s, qrScanResult.data), onDismiss = onQrResultHandled)
    }

    is QrScanResult.Success -> {
      onDataFound(qrScanResult.data)
    }

    null -> {}
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, true)
        .background(Color.Black)
    ) {
      if (hasCameraPermission) {
        CameraScreen(
          state = cameraState,
          emitter = cameraEmitter,
          enableQrScanning = true,
          captureMode = CameraCaptureMode.ImageOnly,
          roundCorners = false,
          fillViewport = true,
          modifier = Modifier.fillMaxSize()
        ) {
          QrCrosshair(modifier = Modifier.fillMaxSize())

          Text(
            text = stringResource(R.string.MollySocketLink_scan_the_qr_code),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 24.dp)
              .background(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape)
              .padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      } else {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(48.dp)
        ) {
          Text(
            text = stringResource(MediaSendR.string.CameraXFragment_to_scan_qr_code_allow_camera),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
          )
          Buttons.MediumTonal(
            colors = ButtonDefaults.filledTonalButtonColors(),
            onClick = onOpenCameraClicked
          ) {
            Text(stringResource(MediaSendR.string.CameraXFragment_allow_access))
          }
        }
      }
      FloatingActionButton(
        shape = CircleShape,
        containerColor = SignalTheme.colors.colorSurface1,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 24.dp),
        onClick = onOpenGalleryClicked
      ) {
        Image(
          painter = painterResource(id = R.drawable.symbol_album_24),
          contentDescription = null,
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
      }
    }
  }
}

@Composable
private fun QrScanResultDialog(title: String? = null, message: String, onDismiss: () -> Unit) {
  Dialogs.SimpleMessageDialog(
    title = title,
    message = message,
    dismiss = stringResource(id = android.R.string.ok),
    onDismiss = onDismiss
  )
}
