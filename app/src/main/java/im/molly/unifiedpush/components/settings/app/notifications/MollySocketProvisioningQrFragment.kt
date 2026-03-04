/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import im.molly.unifiedpush.model.MollySocketDevice
import im.molly.unifiedpush.MollySocketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.registration.ui.link.LinkProvisioningQrContract
import org.thoughtcrime.securesms.registration.ui.link.LinkProvisioningState
import org.thoughtcrime.securesms.registration.ui.link.RegisterLinkDeviceQrViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

class MollySocketProvisioningQrFragment : ComposeFragment() {

  private val viewModel: RegisterLinkDeviceQrViewModel by activityViewModels()
  private val provisioning: LinkProvisioningQrContract
    get() = viewModel
  private val deviceError = MutableStateFlow<String?>(null)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      override fun onPause(owner: LifecycleOwner) {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    })

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        provisioning
          .provisioningState
          .mapNotNull { it.provisionMessage?.provisioningCode }
          .distinctUntilChanged()
          .collect { verificationCode ->
            withContext(Dispatchers.IO) {
              runCatching {
                MollySocketRepository.createDeviceFromVerificationCode(verificationCode)
              }.onSuccess { device ->
                finishWithDevice(device)
              }.onFailure { throwable ->
                deviceError.value = throwable.message ?: "Unable to create linked credential."
              }
            }
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by provisioning.provisioningState.collectAsState()
    val error by deviceError.collectAsState()

    MollySocketProvisioningQrScreen(
      state = state,
      onCancel = { requireActivity().finish() },
      error = error,
      onRetry = {
        deviceError.value = null
        provisioning.clearProvisioningError()
      }
    )
  }

  private fun finishWithDevice(device: MollySocketDevice) {
    val activity = requireActivity() as MollySocketProvisioningQrActivity
    activity.finishWithDevice(device)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MollySocketProvisioningQrScreen(
  state: LinkProvisioningState,
  error: String?,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
) {
  RegistrationScreen(
    title = "Approve on primary device",
    subtitle = null,
    bottomContent = {
      TextButton(
        onClick = onCancel,
        modifier = Modifier.align(Alignment.Center)
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(space = 48.dp, alignment = Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(space = 48.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
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
            when (val qrState = state.qrState) {
              is RegisterLinkDeviceQrViewModel.QrState.Loaded -> {
                QrCode(
                  data = qrState.qrData,
                  foregroundColor = Color(0xFF2449C0),
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                )
              }

              RegisterLinkDeviceQrViewModel.QrState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
              }

              RegisterLinkDeviceQrViewModel.QrState.Scanned,
              RegisterLinkDeviceQrViewModel.QrState.Failed -> {
                val text = if (state.qrState is RegisterLinkDeviceQrViewModel.QrState.Scanned) {
                  "Scanned on primary device"
                } else {
                  stringResource(R.string.RestoreViaQr_qr_code_error)
                }

                Column(
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

      Column(
        modifier = Modifier
          .align(alignment = Alignment.CenterVertically)
          .widthIn(160.dp, 320.dp)
      ) {
        Text(
          text = "Scan this QR code with your primary Signal device to approve a MollySocket credential.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (state.hasProvisioningError || error != null) {
      Dialogs.SimpleMessageDialog(
        message = error ?: stringResource(R.string.RestoreViaQr_qr_code_error),
        onDismiss = onRetry,
        dismiss = stringResource(android.R.string.ok)
      )
    } else if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}

