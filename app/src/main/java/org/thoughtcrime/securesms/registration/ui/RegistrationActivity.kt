/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.ActivityNavigator
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.sms.SmsRetrieverReceiver
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Activity to hold the entire registration process.
 */
class RegistrationActivity : PassphraseRequiredActivity() {

  private val TAG = Log.tag(RegistrationActivity::class.java)

  private val dynamicTheme = DynamicNoActionBarTheme()
  val sharedViewModel: RegistrationViewModel by viewModels()

  private var smsRetrieverReceiver: SmsRetrieverReceiver? = null

  init {
    lifecycle.addObserver(SmsRetrieverObserver())
  }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.activity_registration_navigation_v3)

    sharedViewModel.isReregister = intent.getBooleanExtra(RE_REGISTRATION_EXTRA, false)

    sharedViewModel.checkpoint.observe(this) {
      if (it >= RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE) {
        RegistrationUtil.maybeMarkRegistrationComplete()
        handleSuccessfulVerify()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  private fun handleSuccessfulVerify() {
    if (SignalStore.account.isPrimaryDevice && SignalStore.account.isMultiDevice) {
      SignalStore.misc.shouldShowLinkedDevicesReminder = sharedViewModel.isReregister
    }

    startActivity(MainActivity.clearTop(this))
    finish()
    ActivityNavigator.applyPopAnimationsToPendingTransition(this)
  }

  private inner class SmsRetrieverObserver : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
      smsRetrieverReceiver = SmsRetrieverReceiver(application)
      smsRetrieverReceiver?.registerReceiver()
    }

    override fun onDestroy(owner: LifecycleOwner) {
      smsRetrieverReceiver?.unregisterReceiver()
      smsRetrieverReceiver = null
    }
  }

  companion object {
    const val RE_REGISTRATION_EXTRA: String = "re_registration"

    @JvmStatic
    fun newIntentForNewRegistration(context: Context, originalIntent: Intent): Intent {
      return Intent(context, RegistrationActivity::class.java).apply {
        putExtra(RE_REGISTRATION_EXTRA, false)
        setData(originalIntent.data)
      }
    }

    @JvmStatic
    fun newIntentForReRegistration(context: Context): Intent {
      return Intent(context, RegistrationActivity::class.java).apply {
        putExtra(RE_REGISTRATION_EXTRA, true)
      }
    }
  }
}
