/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.MasterSecretRule
import org.thoughtcrime.securesms.util.SecurePreferenceManager

/** Ensures Signal's old passphrase and screen-lock settings never become Molly's lock configuration. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SettingsValuesLegacyLockTest {

  @get:Rule
  val masterSecret = MasterSecretRule()

  private val context: Application
    get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    PlainTextKeyValueStore.init(context)
    clearLegacyValues()
  }

  @After
  fun tearDown() {
    clearLegacyValues()
  }

  @Test
  fun `legacy Signal lock preferences do not manufacture SignalStore entries`() {
    SecurePreferenceManager.getSecurePreferences(context).edit()
      .putBoolean("pref_disable_passphrase", false)
      .putBoolean("pref_timeout_passphrase", true)
      .putInt("pref_timeout_interval", 300)
      .putBoolean("pref_android_screen_lock", true)
      .putLong("pref_android_screen_lock_timeout", 123)
      .commit()

    val dataSet = KeyValueDataSet()
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet), DirectExecutor())
    SettingsValues(store, context)

    UPSTREAM_STORE_KEYS.forEach { key -> assertFalse("Unexpected key: $key", dataSet.containsKey(key)) }
  }

  @Test
  fun `values written by intermediate builds are not exported as Molly settings`() {
    val dataSet = KeyValueDataSet()
    dataSet.putBoolean("settings.passphrase.disabled", false)
    dataSet.putBoolean("settings.passphrase.timeout.enabled", true)
    dataSet.putInteger("settings.passphrase.timeout", 300)
    dataSet.putBoolean("settings.screen.lock.enabled", true)
    dataSet.putLong("settings.screen.lock.timeout", 123)
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet), DirectExecutor())
    val values = SettingsValues(store, context)

    val backedUpKeys = values.keysToIncludeInBackup.toSet()

    UPSTREAM_STORE_KEYS.forEach { key ->
      assertTrue(dataSet.containsKey(key))
      assertFalse(key, backedUpKeys.contains(key))
    }
  }

  private fun clearLegacyValues() {
    SecurePreferenceManager.getSecurePreferences(context).edit()
      .remove("pref_disable_passphrase")
      .remove("pref_timeout_passphrase")
      .remove("pref_timeout_interval")
      .remove("pref_android_screen_lock")
      .remove("pref_android_screen_lock_timeout")
      .commit()
  }

  companion object {
    private val UPSTREAM_STORE_KEYS = setOf(
      "settings.passphrase.disabled",
      "settings.passphrase.timeout.enabled",
      "settings.passphrase.timeout",
      "settings.screen.lock.enabled",
      "settings.screen.lock.timeout"
    )
  }
}
