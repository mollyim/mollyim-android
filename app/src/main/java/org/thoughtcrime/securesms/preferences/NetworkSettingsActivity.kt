/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Standalone host for Molly's legacy network settings, usable before registration completes.
 *
 * Unlike AppSettingsActivity, this does not extend PassphraseRequiredActivity, so opening network settings during
 * registration cannot reroute the user back to the start of the registration flow.
 */
@SuppressLint("BaseActivitySubclass")
class NetworkSettingsActivity : BaseActivity() {

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)

    super.onCreate(savedInstanceState)
    setContentView(R.layout.nav_graph_host_activity)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.nav_host_fragment, NetworkPreferenceFragment())
        .commitNow()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  companion object {
    @JvmStatic
    fun intent(context: Context): Intent = Intent(context, NetworkSettingsActivity::class.java)
  }
}
