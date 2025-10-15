package org.thoughtcrime.securesms.linkdevice

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.VibrateUtil

/**
 * Fragment that allows users to scan a QR code from their camera to link a device
 */
class AddLinkDeviceFragment : ComposeFragment() {

  companion object {
    private const val VIBRATE_DURATION_MS = 50
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val cameraPermissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    MainScreen(
      state = state,
      navController = navController,
      hasPermissions = cameraPermissionState.status.isGranted,
      linkWithoutQrCode = state.linkWithoutQrCode,
      onRequestPermissions = { askPermissions() },
      onShowFrontCamera = { viewModel.showFrontCamera() },
      onQrCodeScanned = { data ->
        if (VibrateUtil.isHapticFeedbackEnabled(requireContext())) {
          VibrateUtil.vibrate(requireContext(), VIBRATE_DURATION_MS)
        }
        viewModel.onQrCodeScanned(data)
      },
      onLinkNewDeviceWithUrl = { url ->
        navController.popBackStack()
        viewModel.onQrCodeScanned(url)
        viewModel.addDevice(shouldSync = false)
      },
      onQrCodeApproved = {
        navController.popBackStack()
        viewModel.addDevice(shouldSync = false)
      },
      onQrCodeDismissed = { viewModel.onQrCodeDismissed() },
      onQrCodeRetry = { viewModel.onQrCodeScanned(state.linkUri.toString()) },
      onLinkDeviceSuccess = {
        viewModel.onLinkDeviceResult(showSheet = true)
      },
      onLinkDeviceFailure = { viewModel.onLinkDeviceResult(showSheet = false) }
    )
  }

  private fun askPermissions() {
    Permissions.with(this)
      .request(Manifest.permission.CAMERA)
      .ifNecessary()
      .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, parentFragmentManager)
      .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show() }
      .execute()
  }

  @SuppressLint("MissingSuperCall")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }
}

@Composable
private fun MainScreen(
  state: LinkDeviceSettingsState,
  navController: NavController? = null,
  hasPermissions: Boolean = false,
  linkWithoutQrCode: Boolean = false,
  onLinkNewDeviceWithUrl: (String) -> Unit = {},
  onRequestPermissions: () -> Unit = {},
  onShowFrontCamera: () -> Unit = {},
  onQrCodeScanned: (String) -> Unit = {},
  onQrCodeApproved: () -> Unit = {},
  onQrCodeDismissed: () -> Unit = {},
  onQrCodeRetry: () -> Unit = {},
  onLinkDeviceSuccess: () -> Unit = {},
  onLinkDeviceFailure: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = if (linkWithoutQrCode) stringResource(id = R.string.DeviceAddFragment__link_without_scanning) else "",
    onNavigationClick = { navController?.popBackStack() },
    navigationIcon = ImageVector.vectorResource(id = R.drawable.ic_x),
    navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
    actions = {
      if (!linkWithoutQrCode) {
        IconButton(onClick = { onShowFrontCamera() }) {
          Icon(painterResource(id = R.drawable.symbol_switch_24), contentDescription = null)
        }
      }
    }
  ) { contentPadding: PaddingValues ->
    if (!linkWithoutQrCode) {
      LinkDeviceQrScanScreen(
        hasPermission = hasPermissions,
        onRequestPermissions = onRequestPermissions,
        showFrontCamera = state.showFrontCamera,
        qrCodeState = state.qrCodeState,
        onQrCodeScanned = onQrCodeScanned,
        onQrCodeAccepted = onQrCodeApproved,
        onQrCodeDismissed = onQrCodeDismissed,
        onQrCodeRetry = onQrCodeRetry,
        linkDeviceResult = state.linkDeviceResult,
        onLinkDeviceSuccess = onLinkDeviceSuccess,
        onLinkDeviceFailure = onLinkDeviceFailure,
        navController = navController,
        modifier = Modifier.padding(contentPadding)
      )
    } else {
      LinkDeviceManualEntryScreen(
        onLinkNewDeviceWithUrl = onLinkNewDeviceWithUrl,
        linkDeviceResult = state.linkDeviceResult,
        onLinkDeviceSuccess = onLinkDeviceSuccess,
        onLinkDeviceFailure = onLinkDeviceFailure,
        modifier = Modifier.padding(contentPadding)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun LinkDeviceAddScreenPreview() {
  Previews.Preview {
    MainScreen(
      state = LinkDeviceSettingsState()
    )
  }
}
