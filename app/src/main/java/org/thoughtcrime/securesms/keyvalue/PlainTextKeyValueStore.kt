package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.backup.proto.SharedPreference
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SecurePreferenceManager

/**
 * There are some values that you can't store in the normal encrypted [KeyValueStore].
 * Usually, it's because the value you're storing is needed to *open* the database that backs [SignalStore],
 * or is otherwise related to the state of the database itself. Regardless, this is just a normal
 * shared-prefs-backed class.
 *
 * MOLLY: Most values are encrypted by [SecurePreferenceManager]. Non-sensitive settings needed before database unlock,
 * including theme and dynamic colors, are deliberately stored in plaintext through the per-key encryption filter.
 */
object PlainTextKeyValueStore {

  private const val SMS_MIGRATION_ID_OFFSET = "sms_migration_id_offset"
  private const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
  private const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
  private const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
  private const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
  private const val APP_MIGRATION_VERSION = "pref_app_migration_version"
  private const val LAST_VERSION_CODE = "last_version_code"

  const val LANGUAGE = "pref_language"
  const val THEME = "pref_theme"
  const val DYNAMIC_COLORS_ENABLED = "pref_dynamic_colors"
  const val SYSTEM_EMOJI_PREFERRED = "pref_system_emoji"
  const val PASSPHRASE_LOCK_ENABLED = "pref_passphrase_lock"
  const val PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED = "pref_passphrase_lock_notifications"
  const val BIOMETRIC_SCREEN_LOCK_ENABLED = "pref_biometric_screen_lock"
  const val DIRECTORY_REFRESH_TIME = "pref_directory_refresh_time"
  const val REFRESH_FCM_TOKEN_REQUIRED = "pref_refresh_fcm_token"

  /**
   * MOLLY: Legacy key that old installs wrote as a raw int while it was exempt from encryption. Only the V1 misc migration
   * reads it, so it must stay in [org.thoughtcrime.securesms.util.SecurePreferenceManager]'s encryption filter, or that
   * migration throws ClassCastException trying to decrypt it as a string.
   */
  const val FIRST_INSTALL_VERSION = "pref_first_install_version"

  private lateinit var sharedPrefs: SharedPreferences

  /**
   * Must be called from [android.app.Application.attachBaseContext] before anything else has a chance to read from
   * this store.
   */
  @JvmStatic
  fun init(context: Context) {
    sharedPrefs = SecurePreferenceManager.getSecurePreferences(context)
  }

  /** Stores the ID offset that was determined during the big migration that moved all SMS messages into the MMS table. */
  @JvmStatic
  var smsMigrationIdOffset: Long
    get() = sharedPrefs.getLong(SMS_MIGRATION_ID_OFFSET, -1)
    set(value) = sharedPrefs.edit(commit = true) { putLong(SMS_MIGRATION_ID_OFFSET, value) }

  /** The keystore-sealed secret used to open the main database. */
  @JvmStatic
  var databaseEncryptedSecret: String?
    get() = sharedPrefs.getString(DATABASE_ENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(DATABASE_ENCRYPTED_SECRET, value) }

  /** Legacy plaintext database secret, only present until it's been sealed into [databaseEncryptedSecret]. */
  @JvmStatic
  var databaseLegacyUnencryptedSecret: String?
    get() = sharedPrefs.getString(DATABASE_UNENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(DATABASE_UNENCRYPTED_SECRET, value) }

  /** The keystore-sealed secret used to decrypt attachments on disk. */
  @JvmStatic
  var attachmentEncryptedSecret: String?
    get() = sharedPrefs.getString(ATTACHMENT_ENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(ATTACHMENT_ENCRYPTED_SECRET, value) }

  /** Legacy plaintext attachment secret, only present until it's been sealed into [attachmentEncryptedSecret]. */
  @JvmStatic
  var attachmentLegacyUnencryptedSecret: String?
    get() = sharedPrefs.getString(ATTACHMENT_UNENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(ATTACHMENT_UNENCRYPTED_SECRET, value) }

  /** The last [org.thoughtcrime.securesms.migrations.ApplicationMigrations] version that ran to completion. */
  @JvmStatic
  var appMigrationVersion: Int
    get() = sharedPrefs.getInt(APP_MIGRATION_VERSION, 1)
    set(value) = sharedPrefs.edit(commit = true) { putInt(APP_MIGRATION_VERSION, value) }

  /** The version code the app was last launched at. */
  @JvmStatic
  var lastVersionCode: Int
    get() = sharedPrefs.getInt(LAST_VERSION_CODE, BuildConfig.VERSION_CODE)
    set(value) = sharedPrefs.edit(commit = true) { putInt(LAST_VERSION_CODE, value) }

  /** The user's chosen app language, or "zz" to follow the system. */
  @JvmStatic
  var language: String
    get() = sharedPrefs.getString(LANGUAGE, "zz") ?: "zz"
    set(value) = sharedPrefs.edit(commit = true) { putString(LANGUAGE, value) }

  @JvmStatic
  var theme: String
    get() = sharedPrefs.getString(THEME, if (DynamicTheme.systemThemeAvailable()) "system" else "light") ?: "light"
    set(value) = sharedPrefs.edit(commit = true) { putString(THEME, value) }

  @JvmStatic
  var passphraseLockEnabled: Boolean
    get() = sharedPrefs.getBoolean(PASSPHRASE_LOCK_ENABLED, true)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(PASSPHRASE_LOCK_ENABLED, value) }

  @JvmStatic
  var lockNotificationsEnabled: Boolean
    get() = sharedPrefs.getBoolean(PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED, true)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED, value) }

  @JvmStatic
  var biometricScreenLockEnabled: Boolean
    get() = sharedPrefs.getBoolean(BIOMETRIC_SCREEN_LOCK_ENABLED, false)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(BIOMETRIC_SCREEN_LOCK_ENABLED, value) }

  @JvmStatic
  var shouldRefreshFcmToken: Boolean
    get() = sharedPrefs.getBoolean(REFRESH_FCM_TOKEN_REQUIRED, false)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(REFRESH_FCM_TOKEN_REQUIRED, value) }

  /** When the next contact directory refresh is scheduled. */
  @JvmStatic
  var directoryRefreshTime: Long
    get() = sharedPrefs.getLong(DIRECTORY_REFRESH_TIME, 0)
    set(value) = sharedPrefs.edit(commit = true) { putLong(DIRECTORY_REFRESH_TIME, value) }

  @JvmStatic
  fun clearDirectoryRefreshTime() {
    sharedPrefs.edit(commit = true) { remove(DIRECTORY_REFRESH_TIME) }
  }

  @JvmStatic
  var isDynamicColorsEnabled: Boolean
    get() = sharedPrefs.getBoolean(DYNAMIC_COLORS_ENABLED, false)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(DYNAMIC_COLORS_ENABLED, value) }

  @JvmStatic
  var isSystemEmojiPreferred: Boolean
    get() = sharedPrefs.getBoolean(SYSTEM_EMOJI_PREFERRED, false)
    set(value) = sharedPrefs.edit(commit = true) { putBoolean(SYSTEM_EMOJI_PREFERRED, value) }

  /**
   * MOLLY: Settings without a V1 SignalStore mirror retain their preference-based backup records. Dynamic colors use
   * this path deliberately.
   */
  @JvmStatic
  fun getPreferencesToSaveToBackup(): List<SharedPreference> {
    val records = BOOTSTRAP_BOOLEAN_KEYS_TO_BACKUP.mapNotNullTo(mutableListOf()) { key ->
      if (sharedPrefs.contains(key)) {
        SharedPreference.Builder()
          .file_(SecurePreferenceManager.getSecurePreferencesName())
          .key(key)
          .booleanValue(sharedPrefs.getBoolean(key, false))
          .build()
      } else {
        null
      }
    }

    if (sharedPrefs.contains(LANGUAGE)) {
      records.add(
        SharedPreference.Builder()
          .file_(SecurePreferenceManager.getSecurePreferencesName())
          .key(LANGUAGE)
          .value_(sharedPrefs.getString(LANGUAGE, "zz"))
          .build()
      )
    }

    return records
  }

  @JvmStatic
  fun getPreferencesToSaveToBackupCount(): Long = getPreferencesToSaveToBackup().size.toLong()

  private val BOOTSTRAP_BOOLEAN_KEYS_TO_BACKUP = listOf(
    BIOMETRIC_SCREEN_LOCK_ENABLED,
    PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED,
    DYNAMIC_COLORS_ENABLED,
  )
}
