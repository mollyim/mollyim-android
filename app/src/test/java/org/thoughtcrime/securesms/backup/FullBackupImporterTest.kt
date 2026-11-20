package org.thoughtcrime.securesms.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.backup.proto.SharedPreference
import org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList
import org.thoughtcrime.securesms.keyvalue.MollyValues
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.util.SecurePreferenceManager

class FullBackupImporterTest {

  @Test
  fun `computeTableOrder - empty`() {
    val order = FullBackupImporter.computeTableDropOrder(mapOf())

    assertEquals(listOf<String>(), order)
  }

  /**
   * A B C
   */
  @Test
  fun `computeTableOrder - no dependencies`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf(),
        "B" to setOf(),
        "C" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   * A
   * |
   * B
   * |
   * C
   */
  @Test
  fun `computeTableOrder - single chain`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("B"),
        "B" to setOf("C")
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   *   ┌──A──┐    B   C
   * ┌─D   ┌─E─┐
   * F     G   H
   */
  @Test
  fun `computeTableOrder - complex 1`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf(),
        "C" to setOf(),
        "D" to setOf("F"),
        "E" to setOf("G", "H"),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   *   ┌────A────┐
   *   │    |    │
   * ┌─B─┐  C  ┌─D─┐
   * │   │  |  │   │
   * E   F  G  H   I
   */
  @Test
  fun `computeTableOrder - complex 2`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("B", "C", "D"),
        "B" to setOf("E", "F"),
        "C" to setOf("G"),
        "D" to setOf("H", "I"),
        "E" to setOf(),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf(),
        "I" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"), order)
  }

  /**
   * ┌─A─┐  B  ┌─C─┐
   * │   │  |  │   │
   * D   E  F  G   H
   */
  @Test
  fun `computeTableOrder - multiple roots`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf("F"),
        "C" to setOf("G", "H"),
        "D" to setOf(),
        "E" to setOf(),
        "F" to setOf(),
        "G" to setOf(),
        "H" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   * ┌─A─┐  B  ┌─C─┐
   * │   │  |  │   │
   * D   E  D  D   E
   */
  @Test
  fun `computeTableOrder - multiple roots, dupes across graphs`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D", "E"),
        "B" to setOf("D"),
        "C" to setOf("D", "E"),
        "D" to setOf(),
        "E" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E"), order)
  }

  /**
   * A  B
   * │  │
   * D  C
   *    │
   *    D
   */
  @Test
  fun `computeTableOrder - multiple roots, new dependencies across roots`() {
    val order = FullBackupImporter.computeTableDropOrder(
      mapOf(
        "A" to setOf("D"),
        "B" to setOf("C"),
        "C" to setOf("D"),
        "D" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D"), order)
  }

  @Test
  fun `legacy theme and system-emoji preferences populate their SignalStore entries`() {
    val themeMirror = FullBackupImporter.getLegacyPreferenceMirror(legacyPreference(PlainTextKeyValueStore.THEME, BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences").value_("dark").build())!!
    val systemEmojiMirror = FullBackupImporter.getLegacyPreferenceMirror(legacyPreference(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED, BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences").booleanValue(true).build())!!

    assertEquals("dark", themeMirror.getString(SettingsValues.THEME, null))
    assertTrue(systemEmojiMirror.getBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, false))
  }

  @Test
  fun `false bootstrap booleans are retained during conversion`() {
    val preference = legacyPreference(PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED, BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences")
      .booleanValue(false)
      .build()

    val mirror = FullBackupImporter.getLegacyPreferenceMirror(preference)!!

    assertFalse(mirror.getBoolean(SettingsValues.PREFER_SYSTEM_EMOJI, true))
  }

  @Test
  fun `legacy privacy preferences populate their SignalStore mirrors`() {
    val booleanMappings = mapOf(
      "pref_screen_security" to SettingsValues.SCREEN_SECURITY_ENABLED,
      "pref_incognito_keyboard" to SettingsValues.INCOGNITO_KEYBOARD_ENABLED,
      "pref_read_receipts" to SettingsValues.READ_RECEIPTS_ENABLED,
      "pref_typing_indicators" to SettingsValues.TYPING_INDICATORS_ENABLED,
      "pref_show_unidentifed_delivery_indicators" to SettingsValues.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
      "pref_universal_unidentified_access" to SettingsValues.UNIVERSAL_UNIDENTIFIED_ACCESS,
      "pref_turn_only" to SettingsValues.ALWAYS_RELAY_CALLS,
    )

    for ((legacyKey, storeKey) in booleanMappings) {
      val mirror = FullBackupImporter.getLegacyPreferenceMirror(legacyPreference(legacyKey, BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences").booleanValue(true).build())!!
      assertTrue(legacyKey, mirror.getBoolean(storeKey, false))
    }

    val privacyMirror = FullBackupImporter.getLegacyPreferenceMirror(legacyPreference("pref_notification_privacy", BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences").value_("none").build())!!
    assertEquals("none", privacyMirror.getString(SettingsValues.MESSAGE_NOTIFICATION_PRIVACY, null))
  }

  @Test
  fun `legacy Molly scalar preferences retain their values and types`() {
    val booleanKeys = listOf(
      MollyValues.LOG_ENABLED,
      MollyValues.UPDATE_APK_ENABLED,
      MollyValues.UPDATE_APK_INCLUDE_BETA,
      MollyValues.BLOCK_UNKNOWN,
    )
    for (key in booleanKeys) {
      val mirror = FullBackupImporter.getLegacyPreferenceMirror(legacyPreference(key).booleanValue(false).build())!!
      assertFalse(key, mirror.getBoolean(key, true))
    }
  }

  @Test
  fun `legacy passphrase trigger string sets retain their values and type`() {
    val triggers = setOf("timeout", "screen")
    val preference = legacyPreference(MollyValues.PASSPHRASE_LOCK_TRIGGER)
      .isStringSetValue(true)
      .stringSetValue(triggers.toList())
      .build()

    val mirror = FullBackupImporter.getLegacyPreferenceMirror(preference)!!
    val encoded = mirror.getBlob(MollyValues.PASSPHRASE_LOCK_TRIGGER, null)!!
    val decoded = SignalStoreList.ADAPTER.decode(encoded)

    assertEquals(triggers, decoded.contents.toSet())
  }

  @Test
  fun `preference-only empty or unrecognized records do not create SignalStore entries`() {
    val dynamicColorsPreference = legacyPreference(PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED)
      .booleanValue(true)
      .build()
    val unrelatedPreference = legacyPreference("pref_unrelated")
      .booleanValue(true)
      .build()
    val emptyThemePreference = legacyPreference(PlainTextKeyValueStore.THEME).build()
    val wrongFilePreference = SharedPreference.Builder()
      .file_("unrelated_preferences")
      .key(PlainTextKeyValueStore.THEME)
      .value_("dark")
      .build()

    assertNull(FullBackupImporter.getLegacyPreferenceMirror(dynamicColorsPreference))
    assertNull(FullBackupImporter.getLegacyPreferenceMirror(unrelatedPreference))
    assertNull(FullBackupImporter.getLegacyPreferenceMirror(emptyThemePreference))
    assertNull(FullBackupImporter.getLegacyPreferenceMirror(wrongFilePreference))
  }

  private fun legacyPreference(key: String, file: String = SecurePreferenceManager.getSecurePreferencesName()): SharedPreference.Builder {
    return SharedPreference.Builder()
      .file_(file)
      .key(key)
  }
}
