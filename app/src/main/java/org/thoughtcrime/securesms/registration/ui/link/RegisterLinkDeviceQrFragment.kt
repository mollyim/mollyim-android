/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * Crude show QR code on link device to allow linking from primary device.
 */
class RegisterLinkDeviceQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RegisterLinkDeviceQrViewModel by viewModels()

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
        viewModel
          .state
          .mapNotNull { it.provisionMessage }
          .distinctUntilChanged()
          .collect { message ->
            withContext(Dispatchers.IO) {
              val result = sharedViewModel.registerAsLinkedDevice(requireContext().applicationContext, message)

              when (result) {
                RegisterLinkDeviceResult.Success -> Unit
                else -> viewModel.setRegisterAsLinkedDeviceError(result)
              }
            }
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    RegisterLinkDeviceQrScreen(
      state = state,
      onRetryQrCode = viewModel::restartProvisioningSocket,
      onErrorDismiss = viewModel::clearErrors,
      onCancel = { findNavController().popBackStack() }
    )
  }
}

@Composable
private fun RegisterLinkDeviceQrScreen(
  state: RegisterLinkDeviceQrViewModel.RegisterLinkDeviceState,
  onRetryQrCode: () -> Unit = {},
  onErrorDismiss: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  // TODO [link-device] use actual design
  RegistrationScreen(
    title = "Scan this code with your phone",
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
      onRetry = onRetryQrCode
    )

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showProvisioningError) {
      Dialogs.SimpleMessageDialog(
        message = "failed provision",
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    } else if (state.registrationErrorResult != null) {
      val message = when (state.registrationErrorResult) {
        RegisterLinkDeviceResult.IncorrectVerification -> "incorrect verification"
        RegisterLinkDeviceResult.InvalidRequest -> "invalid request"
        RegisterLinkDeviceResult.MaxLinkedDevices -> "max linked devices reached"
        RegisterLinkDeviceResult.MissingCapability -> "missing capability, must update"
        is RegisterLinkDeviceResult.NetworkException -> "network exception ${state.registrationErrorResult.t.message}"
        is RegisterLinkDeviceResult.RateLimited -> "rate limited ${state.registrationErrorResult.retryAfter}"
        is RegisterLinkDeviceResult.UnexpectedException -> "unexpected exception ${state.registrationErrorResult.t.message}"
        RegisterLinkDeviceResult.Success -> throw IllegalStateException()
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

