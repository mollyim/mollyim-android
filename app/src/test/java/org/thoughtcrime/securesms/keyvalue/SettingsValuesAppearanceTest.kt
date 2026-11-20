/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.MasterSecretRule
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SecurePreferenceManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SettingsValuesAppearanceTest {

  @get:Rule
  val masterSecret = MasterSecretRule()

  private val context: Application
    get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    PlainTextKeyValueStore.init(context)
    clearLegacyBootstrapValues()
  }

  @After
  fun tearDown() {
    clearLegacyBootstrapValues()
  }

  @Test
  fun `pre-integration installation migrates theme and system emoji from secure preferences`() {
    legacyPreferences().edit()
      .putString(PlainTextKeyValueStore.THEME, "dark")
      .putBoolean(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED, true)
      .commit()

    // Pre-integration main has no SCREEN_SECURITY_ENABLED migration marker.
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())

    SettingsValues(store, context)

    assertTrue(store.containsKey(SettingsValues.SCREEN_SECURITY_ENABLED))
    assertEquals("dark", store.getString(SettingsValues.THEME, null))
    assertTrue(store.getBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, false))
  }

  @Test
  fun `general migration preserves existing database appearance values`() {
    legacyPreferences().edit()
      .putString(PlainTextKeyValueStore.THEME, "dark")
      .putBoolean(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED, true)
      .commit()

    val dataSet = KeyValueDataSet()
    dataSet.putString(SettingsValues.THEME, "light")
    dataSet.putBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, false)
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet), DirectExecutor())

    SettingsValues(store, context)

    assertEquals("light", store.getString(SettingsValues.THEME, null))
    assertFalse(store.getBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, true))
  }

  @Test
  fun `fresh installation migrates defaults and synchronizes them on first launch`() {
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())
    val values = SettingsValues(store, context)
    val expectedTheme = if (DynamicTheme.systemThemeAvailable()) "system" else "light"

    assertEquals(expectedTheme, store.getString(SettingsValues.THEME, null))
    assertFalse(store.getBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, true))

    values.onFirstEverAppLaunch()

    assertEquals(expectedTheme, PlainTextKeyValueStore.theme)
    assertFalse(PlainTextKeyValueStore.isSystemEmojiPreferred)
  }

  @Test
  fun `direct post-restore synchronization updates theme without changing dynamic colors`() {
    legacyPreferences().edit()
      .putString(PlainTextKeyValueStore.THEME, "light")
      .putBoolean(PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED, true)
      .commit()

    val dataSet = KeyValueDataSet()
    dataSet.putString(SettingsValues.THEME, "dark")
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet), DirectExecutor())
    val values = SettingsValues(store, context)

    values.onPostBackupRestore()

    assertEquals("dark", PlainTextKeyValueStore.theme)
    assertTrue(PlainTextKeyValueStore.isDynamicColorsEnabled)
    assertEquals("dark", store.getString(SettingsValues.THEME, null))
  }

  @Test
  fun `appearance update persists both values before notifying once`() {
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())
    val values = SettingsValues(store, context)
    val events = mutableListOf<String>()

    values.onConfigurationSettingChanged.observeForever { key ->
      events += key
      assertEquals("dark", PlainTextKeyValueStore.theme)
      assertTrue(PlainTextKeyValueStore.isDynamicColorsEnabled)
    }

    values.setAppearance(SettingsValues.Theme.DARK, true)
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(listOf(SettingsValues.THEME), events)
    assertEquals("dark", store.getString(SettingsValues.THEME, null))
    assertTrue(PlainTextKeyValueStore.isDynamicColorsEnabled)
  }

  private fun legacyPreferences() = SecurePreferenceManager.getSecurePreferences(context)

  private fun clearLegacyBootstrapValues() {
    legacyPreferences().edit()
      .remove(PlainTextKeyValueStore.THEME)
      .remove(PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED)
      .remove(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED)
      .commit()
  }
}
