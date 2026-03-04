/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.link.LinkProvisioningQrContract
import org.thoughtcrime.securesms.registration.ui.link.LinkProvisioningQrSection
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
            val result = withContext(Dispatchers.IO) {
              runCatching {
                MollySocketRepository.createDeviceFromVerificationCode(verificationCode)
              }
            }
            result.onSuccess { device ->
              finishWithDevice(device)
            }.onFailure { throwable ->
              deviceError.value = throwable.message ?: "Unable to create linked credential."
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
    LinkProvisioningQrSection(
      qrState = state.qrState,
      onRetry = onRetry
    )

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

