/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import im.molly.unifiedpush.model.MollySocketDevice
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.compose.SignalTheme

class MollySocketProvisioningQrActivity : AppCompatActivity() {

  private val viewModel: MollySocketProvisioningQrViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
          state.device?.let { finishWithDevice(it) }
        }
      }
    }

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      MollySocketProvisioningQrScreen(
        state = state,
        onCancel = { finish() },
        onRetry = viewModel::clearErrorAndRetry
      )
    }
  }

  private fun finishWithDevice(device: MollySocketDevice) {
    val result = Intent()
      .putExtra(EXTRA_DEVICE_ID, device.deviceId)
      .putExtra(EXTRA_PASSWORD, device.password)
    setResult(Activity.RESULT_OK, result)
    finish()
  }

  class Contract : ActivityResultContract<Unit, MollySocketDevice?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(context, MollySocketProvisioningQrActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MollySocketDevice? {
      if (resultCode != Activity.RESULT_OK || intent == null) return null
      val deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, 0)
      val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return null
      return if (deviceId > 0) MollySocketDevice(deviceId = deviceId, password = password) else null
    }
  }

  companion object {
    private const val EXTRA_DEVICE_ID = "device_id"
    private const val EXTRA_PASSWORD = "password"
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MollySocketProvisioningQrScreen(
  state: MollySocketProvisioningQrViewModel.State,
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
              is MollySocketProvisioningQrViewModel.QrState.Loaded -> {
                QrCode(
                  data = qrState.qrData,
                  foregroundColor = Color(0xFF2449C0),
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                )
              }

              MollySocketProvisioningQrViewModel.QrState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
              }

              MollySocketProvisioningQrViewModel.QrState.Scanned,
              MollySocketProvisioningQrViewModel.QrState.Failed -> {
                val text = if (state.qrState is MollySocketProvisioningQrViewModel.QrState.Scanned) {
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

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.error != null) {
      Dialogs.SimpleMessageDialog(
        message = state.error,
        onDismiss = onRetry,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

