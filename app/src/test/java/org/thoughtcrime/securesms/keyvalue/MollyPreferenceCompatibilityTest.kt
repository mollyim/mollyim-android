/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.MasterSecretRule
import org.thoughtcrime.securesms.util.SecurePreferenceManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MollyPreferenceCompatibilityTest {

  @get:Rule
  val masterSecret = MasterSecretRule()

  private val context: Application
    get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    PlainTextKeyValueStore.init(context)
    clearPreferenceState()
  }

  @After
  fun tearDown() {
    clearPreferenceState()
    KeyCachingService.clearMasterSecret()
  }

  @Test
  fun `database and attachment secrets read Molly file and ignore upstream default file`() {
    val rawDefault = PreferenceManager.getDefaultSharedPreferences(context)
    rawDefault.edit()
      .putString(DATABASE_SECRET_KEY, "wrong raw database value")
      .putString(ATTACHMENT_SECRET_KEY, "wrong raw attachment value")
      .commit()

    PlainTextKeyValueStore.databaseEncryptedSecret = SEALED_DATABASE_SECRET
    PlainTextKeyValueStore.attachmentEncryptedSecret = SEALED_ATTACHMENT_SECRET

    assertEquals(SEALED_DATABASE_SECRET, PlainTextKeyValueStore.databaseEncryptedSecret)
    assertEquals(SEALED_ATTACHMENT_SECRET, PlainTextKeyValueStore.attachmentEncryptedSecret)
    assertEquals("wrong raw database value", rawDefault.getString(DATABASE_SECRET_KEY, null))
    assertEquals("wrong raw attachment value", rawDefault.getString(ATTACHMENT_SECRET_KEY, null))

    val rawMollyValues = rawMollyValues()
    val storedDatabaseSecret = rawMollyValues[DATABASE_SECRET_KEY]
    val storedAttachmentSecret = rawMollyValues[ATTACHMENT_SECRET_KEY]
    assertNotNull(storedDatabaseSecret)
    assertNotNull(storedAttachmentSecret)
    assertNotEquals(SEALED_DATABASE_SECRET, storedDatabaseSecret)
    assertNotEquals(SEALED_ATTACHMENT_SECRET, storedAttachmentSecret)
  }

  @Test
  fun `legacy reads do not use upstream default file`() {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(TEST_KEY, "99").commit()
    SecurePreferenceManager.getSecurePreferences(context).edit().putString(TEST_KEY, "17").commit()

    assertEquals(17, LegacySharedPrefs.getIntegerFromString(context, TEST_KEY, 0))
  }

  @Test
  fun `mixed integer and string values retain their values during migration reads`() {
    val preferences = SecurePreferenceManager.getSecurePreferences(context)

    preferences.edit().putInt(TEST_KEY, 42).commit()
    assertEquals(42, LegacySharedPrefs.getIntegerFromString(context, TEST_KEY, 0))

    preferences.edit().putString(TEST_KEY, "73").commit()
    assertEquals(73, LegacySharedPrefs.getIntegerFromString(context, TEST_KEY, 0))

    preferences.edit().putString(TEST_KEY, "not an integer").commit()
    assertEquals(0, LegacySharedPrefs.getIntegerFromString(context, TEST_KEY, 0))

    preferences.edit().remove(TEST_KEY).commit()
    assertEquals(0, LegacySharedPrefs.getIntegerFromString(context, TEST_KEY, 0))
  }

  @Test
  fun `appearance remains readable before unlock without using upstream default file`() {
    PlainTextKeyValueStore.theme = "dark"
    PlainTextKeyValueStore.isDynamicColorsEnabled = true
    KeyCachingService.clearMasterSecret()

    assertEquals("dark", PlainTextKeyValueStore.theme)
    assertTrue(PlainTextKeyValueStore.isDynamicColorsEnabled)
    assertEquals("dark", rawMollyValues()[PlainTextKeyValueStore.THEME])
    assertEquals(true, rawMollyValues()[PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED])
    assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(PlainTextKeyValueStore.THEME))
    assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED))
  }

  @Test
  fun `language remains readable before unlock without exposing it in upstream default file`() {
    PlainTextKeyValueStore.language = "en"
    KeyCachingService.clearMasterSecret()

    assertEquals("en", PlainTextKeyValueStore.language)
    assertEquals("en", rawMollyValues()[PlainTextKeyValueStore.LANGUAGE])
    assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(PlainTextKeyValueStore.LANGUAGE))
  }

  @Test
  fun `settings without a SignalStore backup entry use V1 preference records`() {
    PlainTextKeyValueStore.biometricScreenLockEnabled = true
    PlainTextKeyValueStore.lockNotificationsEnabled = false
    PlainTextKeyValueStore.isDynamicColorsEnabled = false
    PlainTextKeyValueStore.language = "en"

    val exported = PlainTextKeyValueStore.getPreferencesToSaveToBackup()

    assertEquals(4L, PlainTextKeyValueStore.getPreferencesToSaveToBackupCount())
    assertEquals(
      setOf(
        PlainTextKeyValueStore.BIOMETRIC_SCREEN_LOCK_ENABLED,
        PlainTextKeyValueStore.PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED,
        PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED,
        PlainTextKeyValueStore.LANGUAGE,
      ),
      exported.map { it.key }.toSet(),
    )
    assertTrue(exported.all { it.file_ == SecurePreferenceManager.getSecurePreferencesName() })
    assertEquals("en", exported.single { it.key == PlainTextKeyValueStore.LANGUAGE }.value_)
    assertEquals(false, exported.single { it.key == PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED }.booleanValue)
  }

  @Test
  fun `legacy plaintext first-install integer migrates without decryption`() {
    // Old installs wrote this key as a raw int while it was exempt from encryption. Use the same cached preferences
    // as the migration so the test remains isolated from Robolectric's per-method Application instances.
    SecurePreferenceManager.getSecurePreferences(context)
      .edit()
      .putInt(PlainTextKeyValueStore.FIRST_INSTALL_VERSION, 550)
      .commit()

    // The exemption must still write a raw Int. An encrypted String here would make this fixture unable to detect
    // the ClassCastException/decryption regression it is intended to cover.
    assertEquals(550, rawMollyValues()[PlainTextKeyValueStore.FIRST_INSTALL_VERSION])

    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())

    MiscellaneousValues(store, context)

    assertEquals(550, store.getInteger("misc.first_install_version", -1))
  }

  private fun clearPreferenceState() {
    SecurePreferenceManager.getSecurePreferences(context)
      .edit()
      .remove(TEST_KEY)
      .remove(DATABASE_SECRET_KEY)
      .remove(ATTACHMENT_SECRET_KEY)
      .remove(PlainTextKeyValueStore.LANGUAGE)
      .remove(PlainTextKeyValueStore.BIOMETRIC_SCREEN_LOCK_ENABLED)
      .remove(PlainTextKeyValueStore.PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED)
      .remove(PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED)
      .remove(PlainTextKeyValueStore.THEME)
      .remove(PlainTextKeyValueStore.FIRST_INSTALL_VERSION)
      .commit()

    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
  }

  // EncryptedPreferences.getAll() deliberately exposes the wrapped store's raw values without decrypting them.
  private fun rawMollyValues(): Map<String, *> = SecurePreferenceManager.getSecurePreferences(context).all

  companion object {
    private const val TEST_KEY = "pref_test_molly_mixed_type"
    private const val DATABASE_SECRET_KEY = "pref_database_encrypted_secret"
    private const val ATTACHMENT_SECRET_KEY = "pref_attachment_encrypted_secret"
    private const val SEALED_DATABASE_SECRET = "serialized keystore-sealed database test value"
    private const val SEALED_ATTACHMENT_SECRET = "serialized keystore-sealed attachment test value"
  }
}
