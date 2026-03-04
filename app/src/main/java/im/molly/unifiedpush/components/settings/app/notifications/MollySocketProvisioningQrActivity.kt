/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.unifiedpush.components.settings.app.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import im.molly.unifiedpush.model.MollySocketDevice

class MollySocketProvisioningQrActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager.commit {
        replace(android.R.id.content, MollySocketProvisioningQrFragment())
      }
    }
  }

  fun finishWithDevice(device: MollySocketDevice) {
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

