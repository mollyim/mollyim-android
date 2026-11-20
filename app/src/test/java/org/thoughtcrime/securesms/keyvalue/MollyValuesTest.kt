/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
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
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.net.ProxyType
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.MasterSecretRule
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MollyValuesTest {

  @get:Rule
  val masterSecret = MasterSecretRule()

  private val context: Application
    get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    clearLegacyValues()
  }

  @After
  fun tearDown() {
    clearLegacyValues()
  }

  @Test
  fun `legacy Molly preferences retain their values and types during migration`() {
    val triggers = setOf("timeout", "screen")
    legacyPreferences().edit()
      .putBoolean(MollyValues.LOG_ENABLED, false)
      .putBoolean(MollyValues.UPDATE_APK_ENABLED, true)
      .putBoolean(MollyValues.UPDATE_APK_INCLUDE_BETA, true)
      .putBoolean(MollyValues.BLOCK_UNKNOWN, true)
      .putLong(MollyValues.BACKUP_INTERVAL, TimeUnit.DAYS.toMillis(7))
      .putInt(MollyValues.BACKUP_MAX_FILES, 8)
      .putString(MollyValues.PROXY_TYPE, ProxyType.SOCKS5.code)
      .putString(MollyValues.PROXY_SOCKS_HOST, "10.0.0.1")
      .putString(MollyValues.PROXY_SOCKS_PORT, "9150")
      .putBoolean(MollyValues.NETWORK_CONFIG_SEEN, false)
      .putInt(MollyValues.PASSPHRASE_LOCK_TIMEOUT, 90)
      .putStringSet(MollyValues.PASSPHRASE_LOCK_TRIGGER, triggers)
      .commit()

    val values = createValues()

    assertFalse(values.logEnabled)
    assertTrue(values.updateApkEnabled)
    assertTrue(values.updateApkIncludeBetaEnabled)
    assertTrue(values.blockUnknownEnabled)
    assertEquals(TimeUnit.DAYS.toMillis(7), values.backupInterval)
    assertEquals(8, values.backupMaxFiles)
    assertEquals(ProxyType.SOCKS5, values.proxyType)
    assertEquals("10.0.0.1", values.proxySocksHost)
    assertEquals("9150", values.proxySocksPort)
    assertFalse(values.hasSeenNetworkConfig)
    assertEquals(90L, values.passphraseLockTimeout)
    assertEquals(triggers, values.passphraseLockTrigger.triggers)
  }

  @Test
  fun `migration never replaces an existing database value with a legacy preference`() {
    legacyPreferences().edit().putBoolean(MollyValues.LOG_ENABLED, true).commit()
    val backingStore = KeyValueDataSet()
    backingStore.putBoolean(MollyValues.LOG_ENABLED, false)

    val values = createValues(backingStore)

    assertFalse(values.logEnabled)
  }

  @Test
  fun `empty stores keep their defaults without manufacturing database entries`() {
    val dataSet = KeyValueDataSet()
    val values = createValues(dataSet)

    assertTrue(values.logEnabled)
    assertFalse(values.updateApkEnabled)
    assertFalse(dataSet.containsKey(MollyValues.LOG_ENABLED))
  }

  @Test
  fun `passphrase trigger writes use the existing SignalStore key and retain their type`() {
    val dataSet = KeyValueDataSet()
    val values = createValues(dataSet)

    values.passphraseLockTrigger = PassphraseLockTriggerPreference(emptySet())
    values.passphraseLockTimeout = 0

    val restored = createValues(dataSet)
    assertTrue(dataSet.containsKey(MollyValues.PASSPHRASE_LOCK_TRIGGER))
    assertEquals(emptySet<String>(), restored.passphraseLockTrigger.triggers)
    assertEquals(0L, restored.passphraseLockTimeout)
  }

  @Test
  fun `Molly settings declare their SignalStore backup keys`() {
    val values = createValues()

    assertEquals(BACKED_UP_KEYS, values.keysToIncludeInBackup.toSet())
  }

  private fun createValues(initialData: KeyValueDataSet = KeyValueDataSet()): MollyValues {
    val persistentStorage = MockKeyValuePersistentStorage.withDataSet(initialData)
    return MollyValues(KeyValueStore(persistentStorage, DirectExecutor()), context)
  }

  private fun clearLegacyValues() {
    val editor = legacyPreferences().edit()
    BACKED_UP_KEYS.forEach { key -> editor.remove(key) }
    editor.commit()
  }

  private fun legacyPreferences() = SecurePreferenceManager.getSecurePreferences(context)

  companion object {
    private val BACKED_UP_KEYS = setOf(
      MollyValues.LOG_ENABLED,
      MollyValues.UPDATE_APK_ENABLED,
      MollyValues.UPDATE_APK_INCLUDE_BETA,
      MollyValues.BLOCK_UNKNOWN,
      MollyValues.BACKUP_INTERVAL,
      MollyValues.BACKUP_MAX_FILES,
      MollyValues.PROXY_TYPE,
      MollyValues.PROXY_SOCKS_HOST,
      MollyValues.PROXY_SOCKS_PORT,
      MollyValues.NETWORK_CONFIG_SEEN,
      MollyValues.PASSPHRASE_LOCK_TIMEOUT,
      MollyValues.PASSPHRASE_LOCK_TRIGGER,
    )
  }
}
