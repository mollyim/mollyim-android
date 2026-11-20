/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import org.signal.core.util.StringStringSerializer
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.databaseprotos.SignalStoreList
import org.thoughtcrime.securesms.net.ProxyType
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference
import java.util.concurrent.TimeUnit

/** Molly-owned settings that are needed only after unlock and can safely live in the encrypted SignalStore database. */
class MollyValues internal constructor(store: KeyValueStore, private val context: Context) : SignalStoreValues(store) {
  companion object {
    private const val LEGACY_MIGRATION_COMPLETE = "molly.legacy_preferences_migrated"

    const val LOG_ENABLED = "pref_log_enabled"
    const val UPDATE_APK_ENABLED = "pref_update_apk_enabled"
    const val UPDATE_APK_INCLUDE_BETA = "pref_update_apk_include_beta"
    const val BLOCK_UNKNOWN = "pref_block_unknown"
    const val BACKUP_INTERVAL = "pref_backup_interval"
    const val BACKUP_MAX_FILES = "pref_backup_max_files"
    const val PROXY_TYPE = "pref_proxy_type"
    const val PROXY_SOCKS_HOST = "pref_proxy_socks_host"
    const val PROXY_SOCKS_PORT = "pref_proxy_socks_port"
    const val NETWORK_CONFIG_SEEN = "pref_network_config_seen"
    const val PASSPHRASE_LOCK_TIMEOUT = "pref_passphrase_lock_timeout"
    const val PASSPHRASE_LOCK_TRIGGER = "pref_passphrase_lock_trigger"

  }

  init {
    migrateLegacySharedPrefs()
  }

  var logEnabled: Boolean by booleanValue(LOG_ENABLED, true)
  var updateApkEnabled: Boolean by booleanValue(UPDATE_APK_ENABLED, false)
  var updateApkIncludeBetaEnabled: Boolean by booleanValue(UPDATE_APK_INCLUDE_BETA, false)
  var blockUnknownEnabled: Boolean by booleanValue(BLOCK_UNKNOWN, false)
  var backupInterval: Long by longValue(BACKUP_INTERVAL, TimeUnit.DAYS.toMillis(1))
  var backupMaxFiles: Int by integerValue(BACKUP_MAX_FILES, 2)
  var hasSeenNetworkConfig: Boolean by booleanValue(NETWORK_CONFIG_SEEN, true)
  var passphraseLockTimeout: Long by longValue(PASSPHRASE_LOCK_TIMEOUT, 0)

  var proxyType: ProxyType
    get() = ProxyType.fromCode(getString(PROXY_TYPE, null))
    set(value) = putString(PROXY_TYPE, value.code)

  var proxySocksHost: String by stringValue(PROXY_SOCKS_HOST, "localhost")
  var proxySocksPort: String by stringValue(PROXY_SOCKS_PORT, "9050")

  var passphraseLockTrigger: PassphraseLockTriggerPreference
    get() {
      val triggers = if (store.containsKey(PASSPHRASE_LOCK_TRIGGER)) {
        getList(PASSPHRASE_LOCK_TRIGGER, StringStringSerializer).requireNoNulls().toSet()
      } else {
        context.resources.getStringArray(R.array.pref_passphrase_lock_trigger_default).toSet()
      }
      return PassphraseLockTriggerPreference(triggers)
    }
    set(value) = putList(PASSPHRASE_LOCK_TRIGGER, value.triggers.toList(), StringStringSerializer)

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    LOG_ENABLED,
    UPDATE_APK_ENABLED,
    UPDATE_APK_INCLUDE_BETA,
    BLOCK_UNKNOWN,
    BACKUP_INTERVAL,
    BACKUP_MAX_FILES,
    PROXY_TYPE,
    PROXY_SOCKS_HOST,
    PROXY_SOCKS_PORT,
    NETWORK_CONFIG_SEEN,
    PASSPHRASE_LOCK_TIMEOUT,
    PASSPHRASE_LOCK_TRIGGER,
  )

  /** Direct upgrades only. Legacy V1 backup records are converted individually by FullBackupImporter. */
  private fun migrateLegacySharedPrefs() {
    if (store.getBoolean(LEGACY_MIGRATION_COMPLETE, false)) {
      return
    }

    val writer = store.beginWrite()

    putLegacyBoolean(writer, LOG_ENABLED, true)
    putLegacyBoolean(writer, UPDATE_APK_ENABLED, false)
    putLegacyBoolean(writer, UPDATE_APK_INCLUDE_BETA, false)
    putLegacyBoolean(writer, BLOCK_UNKNOWN, false)
    putLegacyBoolean(writer, NETWORK_CONFIG_SEEN, true)

    putLegacyLong(writer, BACKUP_INTERVAL, TimeUnit.DAYS.toMillis(1))
    putLegacyLong(writer, PASSPHRASE_LOCK_TIMEOUT, 0)
    putLegacyInt(writer, BACKUP_MAX_FILES, 2)

    putLegacyString(writer, PROXY_TYPE)
    putLegacyString(writer, PROXY_SOCKS_HOST)
    putLegacyString(writer, PROXY_SOCKS_PORT)

    if (!store.containsKey(PASSPHRASE_LOCK_TRIGGER)) {
      val triggers = LegacySharedPrefs.getStringSet(context, PASSPHRASE_LOCK_TRIGGER, null)
      if (triggers != null) {
        writer.putBlob(PASSPHRASE_LOCK_TRIGGER, SignalStoreList(contents = triggers.toList()).encode())
      }
    }

    writer.putBoolean(LEGACY_MIGRATION_COMPLETE, true)
    writer.commit()
  }

  private fun putLegacyBoolean(writer: KeyValueStore.Writer, key: String, defaultValue: Boolean) {
    if (!store.containsKey(key) && LegacySharedPrefs.contains(context, key)) {
      writer.putBoolean(key, LegacySharedPrefs.getBoolean(context, key, defaultValue))
    }
  }

  private fun putLegacyInt(writer: KeyValueStore.Writer, key: String, defaultValue: Int) {
    if (!store.containsKey(key) && LegacySharedPrefs.contains(context, key)) {
      writer.putInteger(key, LegacySharedPrefs.getInteger(context, key, defaultValue))
    }
  }

  private fun putLegacyLong(writer: KeyValueStore.Writer, key: String, defaultValue: Long) {
    if (store.containsKey(key) || !LegacySharedPrefs.contains(context, key)) {
      return
    }

    if (key == PASSPHRASE_LOCK_TIMEOUT) {
      // Historical backups exposed this timeout as an integer although its runtime getter used a long.
      val longValue = LegacySharedPrefs.getLong(context, key, Long.MIN_VALUE)
      if (longValue != Long.MIN_VALUE) {
        writer.putLong(key, longValue)
      } else {
        writer.putLong(key, LegacySharedPrefs.getInteger(context, key, defaultValue.toInt()).toLong())
      }
    } else {
      writer.putLong(key, LegacySharedPrefs.getLong(context, key, defaultValue))
    }
  }

  private fun putLegacyString(writer: KeyValueStore.Writer, key: String) {
    if (!store.containsKey(key) && LegacySharedPrefs.contains(context, key)) {
      writer.putString(key, LegacySharedPrefs.getStringOrNull(context, key))
    }
  }
}
