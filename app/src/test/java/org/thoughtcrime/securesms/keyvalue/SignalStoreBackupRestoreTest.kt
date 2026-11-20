/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SignalStoreBackupRestoreTest {

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
    SignalStore.testInject(null)
    clearLegacyValues()
  }

  @Test
  fun `current KeyValue restore is not overwritten by stale local preferences`() {
    legacyPreferences().edit()
      .putBoolean(MollyValues.LOG_ENABLED, false)
      .putBoolean("pref_read_receipts", false)
      .putString("pref_notification_privacy", "none")
      .putString(PlainTextKeyValueStore.THEME, "light")
      .putBoolean(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED, false)
      .commit()

    val restoredData = KeyValueDataSet()
    restoredData.putBoolean(MollyValues.LOG_ENABLED, true)
    restoredData.putBoolean(SettingsValues.SCREEN_SECURITY_ENABLED, true)
    restoredData.putBoolean(SettingsValues.READ_RECEIPTS_ENABLED, true)
    restoredData.putString(SettingsValues.MESSAGE_NOTIFICATION_PRIVACY, "all")
    restoredData.putString(SettingsValues.THEME, "dark")
    restoredData.putBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, true)

    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(restoredData), DirectExecutor())
    SignalStore.testInject(SignalStore(context, store))

    SignalStore.onPostBackupRestore()

    assertTrue(SignalStore.molly.logEnabled)
    assertTrue(SignalStore.settings.isReadReceiptsEnabled)
    assertEquals("all", SignalStore.settings.messageNotificationsPrivacy.toString())
    assertEquals("dark", PlainTextKeyValueStore.theme)
    assertTrue(PlainTextKeyValueStore.isSystemEmojiPreferred)
  }

  private fun legacyPreferences() = SecurePreferenceManager.getSecurePreferences(context)

  private fun clearLegacyValues() {
    legacyPreferences().edit()
      .remove(MollyValues.LOG_ENABLED)
      .remove("pref_read_receipts")
      .remove("pref_notification_privacy")
      .remove(PlainTextKeyValueStore.THEME)
      .remove(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED)
      .commit()
  }
}
