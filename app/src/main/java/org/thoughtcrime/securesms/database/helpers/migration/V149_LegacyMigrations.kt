@file:Suppress("DEPRECATION")

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.CursorUtil
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.color.MaterialColor
import org.thoughtcrime.securesms.contacts.avatars.ContactColorsLegacy
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations
import org.thoughtcrime.securesms.database.model.databaseprotos.ReactionList
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.FileUtils
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.Triple
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.Locale
import java.util.UUID

/**
 * Adding an urgent flag to message envelopes to help with notifications. Need to track flag in
 * MSL table so can be resent with the correct urgency.
 */
@Suppress("ClassName")
object V149_LegacyMigrations : SignalDatabaseMigration {

  private val TAG: String = SignalDatabaseMigrations.TAG

  private const val REACTIONS_UNREAD_INDEX = 39
  private const val RESUMABLE_DOWNLOADS = 40
  private const val KEY_VALUE_STORE = 41
  private const val ATTACHMENT_DISPLAY_ORDER = 42
  private const val SPLIT_PROFILE_NAMES = 43
  private const val STICKER_PACK_ORDER = 44
  private const val MEGAPHONES = 45
  private const val MEGAPHONE_FIRST_APPEARANCE = 46
  private const val PROFILE_KEY_TO_DB = 47
  private const val PROFILE_KEY_CREDENTIALS = 48
  private const val ATTACHMENT_FILE_INDEX = 49
  private const val STORAGE_SERVICE_ACTIVE = 50
  private const val GROUPS_V2_RECIPIENT_CAPABILITY = 51
  private const val TRANSFER_FILE_CLEANUP = 52
  private const val PROFILE_DATA_MIGRATION = 53
  private const val AVATAR_LOCATION_MIGRATION = 54
  private const val GROUPS_V2 = 55
  private const val ATTACHMENT_UPLOAD_TIMESTAMP = 56
  private const val ATTACHMENT_CDN_NUMBER = 57
  private const val JOB_INPUT_DATA = 58
  private const val SERVER_TIMESTAMP = 59
  private const val REMOTE_DELETE = 60
  private const val COLOR_MIGRATION = 61
  private const val LAST_SCROLLED = 62
  private const val LAST_PROFILE_FETCH = 63
  private const val SERVER_DELIVERED_TIMESTAMP = 64
  private const val QUOTE_CLEANUP = 65
  private const val BORDERLESS = 66
  private const val REMAPPED_RECORDS = 67
  private const val MENTIONS = 68
  private const val PINNED_CONVERSATIONS = 69
  private const val MENTION_GLOBAL_SETTING_MIGRATION = 70
  private const val UNKNOWN_STORAGE_FIELDS = 71
  private const val STICKER_CONTENT_TYPE = 72
  private const val STICKER_EMOJI_IN_NOTIFICATIONS = 73
  private const val THUMBNAIL_CLEANUP = 74
  private const val STICKER_CONTENT_TYPE_CLEANUP = 75
  private const val MENTION_CLEANUP = 76
  private const val MENTION_CLEANUP_V2 = 77
  private const val REACTION_CLEANUP = 78
  private const val CAPABILITIES_REFACTOR = 79
  private const val GV1_MIGRATION = 80
  private const val NOTIFIED_TIMESTAMP = 81
  private const val GV1_MIGRATION_LAST_SEEN = 82
  private const val VIEWED_RECEIPTS = 83
  private const val CLEAN_UP_GV1_IDS = 84
  private const val GV1_MIGRATION_REFACTOR = 85
  private const val CLEAR_PROFILE_KEY_CREDENTIALS = 86
  private const val LAST_RESET_SESSION_TIME = 87
  private const val WALLPAPER = 88
  private const val ABOUT = 89
  private const val SPLIT_SYSTEM_NAMES = 90
  private const val PAYMENTS = 91
  private const val CLEAN_STORAGE_IDS = 92
  private const val MP4_GIF_SUPPORT = 93
  private const val BLUR_AVATARS = 94
  private const val CLEAN_STORAGE_IDS_WITHOUT_INFO = 95
  private const val CLEAN_REACTION_NOTIFICATIONS = 96
  private const val STORAGE_SERVICE_REFACTOR = 97
  private const val CLEAR_MMS_STORAGE_IDS = 98
  private const val SERVER_GUID = 99
  private const val CHAT_COLORS = 100
  private const val AVATAR_COLORS = 101
  private const val EMOJI_SEARCH = 102
  private const val SENDER_KEY = 103
  private const val MESSAGE_DUPE_INDEX = 104
  private const val MESSAGE_LOG = 105
  private const val MESSAGE_LOG_2 = 106
  private const val ABANDONED_MESSAGE_CLEANUP = 107
  private const val THREAD_AUTOINCREMENT = 108
  private const val MMS_AUTOINCREMENT = 109
  private const val ABANDONED_ATTACHMENT_CLEANUP = 110
  private const val AVATAR_PICKER = 111
  private const val THREAD_CLEANUP = 112
  private const val SESSION_MIGRATION = 113
  private const val IDENTITY_MIGRATION = 114
  private const val GROUP_CALL_RING_TABLE = 115
  private const val CLEANUP_SESSION_MIGRATION = 116
  private const val RECEIPT_TIMESTAMP = 117
  private const val BADGES = 118
  private const val SENDER_KEY_UUID = 119
  private const val SENDER_KEY_SHARED_TIMESTAMP = 120
  private const val REACTION_REFACTOR = 121
  private const val PNI = 122
  private const val NOTIFICATION_PROFILES = 123
  private const val NOTIFICATION_PROFILES_END_FIX = 124
  private const val REACTION_BACKUP_CLEANUP = 125
  private const val REACTION_REMOTE_DELETE_CLEANUP = 126
  private const val PNI_CLEANUP = 127
  private const val MESSAGE_RANGES = 128
  private const val REACTION_TRIGGER_FIX = 129
  private const val PNI_STORES = 130
  private const val DONATION_RECEIPTS = 131
  private const val STORIES = 132
  private const val ALLOW_STORY_REPLIES = 133
  private const val GROUP_STORIES = 134
  private const val MMS_COUNT_INDEX = 135
  private const val STORY_SENDS = 136
  private const val STORY_TYPE_AND_DISTRIBUTION = 137
  private const val CLEAN_DELETED_DISTRIBUTION_LISTS = 138
  private const val REMOVE_KNOWN_UNKNOWNS = 139
  private const val CDS_V2 = 140
  private const val GROUP_SERVICE_ID = 141
  private const val QUOTE_TYPE = 142
  private const val STORY_SYNCS = 143
  private const val GROUP_STORY_NOTIFICATIONS = 144
  private const val GROUP_STORY_REPLY_CLEANUP = 145
  private const val REMOTE_MEGAPHONE = 146
  private const val QUOTE_INDEX = 147
  private const val MY_STORY_PRIVACY_MODE = 148
  private const val EXPIRING_PROFILE_CREDENTIALS = 149

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < REACTIONS_UNREAD_INDEX) {
      throw AssertionError("Unsupported Signal database: version is too old")
    }

    if (oldVersion < RESUMABLE_DOWNLOADS) {
      db.execSQL("ALTER TABLE part ADD COLUMN transfer_file TEXT DEFAULT NULL")
    }

    if (oldVersion < KEY_VALUE_STORE) {
      db.execSQL(
        // language=sql
        """
        CREATE TABLE key_value (
          _id INTEGER PRIMARY KEY AUTOINCREMENT,
          key TEXT UNIQUE, 
          value TEXT, 
          type INTEGER
        )
        """.trimIndent()
      )
    }

    if (oldVersion < ATTACHMENT_DISPLAY_ORDER) {
      db.execSQL("ALTER TABLE part ADD COLUMN display_order INTEGER DEFAULT 0")
    }

    if (oldVersion < SPLIT_PROFILE_NAMES) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN profile_family_name TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN profile_joined_name TEXT DEFAULT NULL")
    }

    if (oldVersion < STICKER_PACK_ORDER) {
      db.execSQL("ALTER TABLE sticker ADD COLUMN pack_order INTEGER DEFAULT 0")
    }

    if (oldVersion < MEGAPHONES) {
      db.execSQL(
        // language=sql
        """
        CREATE TABLE megaphone (
          _id INTEGER PRIMARY KEY AUTOINCREMENT,
          event TEXT UNIQUE,
          seen_count INTEGER,
          last_seen INTEGER,
          finished INTEGER
        )
        """.trimIndent()
      )
    }

    if (oldVersion < MEGAPHONE_FIRST_APPEARANCE) {
      db.execSQL("ALTER TABLE megaphone ADD COLUMN first_visible INTEGER DEFAULT 0")
    }

    if (oldVersion < PROFILE_KEY_TO_DB) {
      val localNumber = SecurePreferenceManager.getSecurePreferences(context).getString("pref_local_number", null)
      if (!TextUtils.isEmpty(localNumber)) {
        val encodedProfileKey = SecurePreferenceManager.getSecurePreferences(context).getString("pref_profile_key", null)
        val profileKey = if (encodedProfileKey != null) Base64.decodeOrThrow(encodedProfileKey) else Util.getSecretBytes(32)
        val values = ContentValues(1).apply {
          put("profile_key", Base64.encodeWithPadding(profileKey))
        }
        if (db.update("recipient", values, "phone = ?", arrayOf(localNumber)) == 0) {
          throw AssertionError("No rows updated!")
        }
      }
    }

    if (oldVersion < PROFILE_KEY_CREDENTIALS) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN profile_key_credential TEXT DEFAULT NULL")
    }

    if (oldVersion < ATTACHMENT_FILE_INDEX) {
      db.execSQL("CREATE INDEX IF NOT EXISTS part_data_index ON part (_data)")
    }

    if (oldVersion < STORAGE_SERVICE_ACTIVE) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN group_type INTEGER DEFAULT 0")
      db.execSQL("CREATE INDEX IF NOT EXISTS recipient_group_type_index ON recipient (group_type)")
      db.execSQL("UPDATE recipient set group_type = 1 WHERE group_id NOT NULL AND group_id LIKE '__signal_mms_group__%'")
      db.execSQL("UPDATE recipient set group_type = 2 WHERE group_id NOT NULL AND group_id LIKE '__textsecure_group__%'")
      db.rawQuery("SELECT _id FROM recipient WHERE registered = 1 or group_type = 2", null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val id: String = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
          val values = ContentValues(2).apply {
            put("dirty", 2)
            put("storage_service_key", Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
          }
          db.update("recipient", values, "_id = ?", arrayOf(id))
        }
      }
    }

    if (oldVersion < GROUPS_V2_RECIPIENT_CAPABILITY) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN gv2_capability INTEGER DEFAULT 0")
    }

    if (oldVersion < TRANSFER_FILE_CLEANUP) {
      val partsDirectory: File = context.getDir("parts", Context.MODE_PRIVATE)
      if (partsDirectory.exists()) {
        val transferFiles: Array<File> = partsDirectory.listFiles { _: File?, name: String -> name.startsWith("transfer") } ?: emptyArray()
        var deleteCount = 0
        Log.i(TAG, "Found " + transferFiles.size + " dangling transfer files.")
        for (file: File in transferFiles) {
          if (file.delete()) {
            Log.i(TAG, "Deleted " + file.name)
            deleteCount++
          }
        }
        Log.i(TAG, "Deleted $deleteCount dangling transfer files.")
      } else {
        Log.w(TAG, "Part directory did not exist. Skipping.")
      }
    }

    if (oldVersion < PROFILE_DATA_MIGRATION) {
      val localNumber = SecurePreferenceManager.getSecurePreferences(context).getString("pref_local_number", null)
      if (localNumber != null) {
        val encodedProfileName = SecurePreferenceManager.getSecurePreferences(context).getString("pref_profile_name", null)
        val profileName = ProfileName.fromSerialized(encodedProfileName)
        db.execSQL("UPDATE recipient SET signal_profile_name = ?, profile_family_name = ?, profile_joined_name = ? WHERE phone = ?", arrayOf(profileName.givenName, profileName.familyName, profileName.toString(), localNumber))
      }
    }

    if (oldVersion < AVATAR_LOCATION_MIGRATION) {
      val oldAvatarDirectory = File(context.filesDir, "avatars")
      if (!FileUtils.deleteDirectory(oldAvatarDirectory)) {
        Log.w(TAG, "Failed to delete avatar directory.")
      }
      db.execSQL("UPDATE recipient SET signal_profile_avatar = NULL")
      db.execSQL("UPDATE groups SET avatar_id = 0 WHERE avatar IS NULL")
      db.execSQL("UPDATE groups SET avatar = NULL")
    }

    if (oldVersion < GROUPS_V2) {
      db.execSQL("ALTER TABLE groups ADD COLUMN master_key")
      db.execSQL("ALTER TABLE groups ADD COLUMN revision")
      db.execSQL("ALTER TABLE groups ADD COLUMN decrypted_group")
    }

    if (oldVersion < ATTACHMENT_UPLOAD_TIMESTAMP) {
      db.execSQL("ALTER TABLE part ADD COLUMN upload_timestamp DEFAULT 0")
    }

    if (oldVersion < ATTACHMENT_CDN_NUMBER) {
      db.execSQL("ALTER TABLE part ADD COLUMN cdn_number INTEGER DEFAULT 0")
    }

    if (oldVersion < JOB_INPUT_DATA) {
      db.execSQL("ALTER TABLE job_spec ADD COLUMN serialized_input_data TEXT DEFAULT NULL")
    }

    if (oldVersion < SERVER_TIMESTAMP) {
      db.execSQL("ALTER TABLE sms ADD COLUMN date_server INTEGER DEFAULT -1")
      db.execSQL("CREATE INDEX IF NOT EXISTS sms_date_server_index ON sms (date_server)")
      db.execSQL("ALTER TABLE mms ADD COLUMN date_server INTEGER DEFAULT -1")
      db.execSQL("CREATE INDEX IF NOT EXISTS mms_date_server_index ON mms (date_server)")
    }

    if (oldVersion < REMOTE_DELETE) {
      db.execSQL("ALTER TABLE sms ADD COLUMN remote_deleted INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE mms ADD COLUMN remote_deleted INTEGER DEFAULT 0")
    }

    if (oldVersion < COLOR_MIGRATION) {
      db.rawQuery("SELECT _id, system_display_name FROM recipient WHERE system_display_name NOT NULL AND color IS NULL", null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val id: Long = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
          val name: String = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"))
          val values = ContentValues()
          values.put("color", ContactColorsLegacy.generateForV2(name).serialize())
          db.update("recipient", values, "_id = ?", arrayOf(id.toString()))
        }
      }
    }

    if (oldVersion < LAST_SCROLLED) {
      db.execSQL("ALTER TABLE thread ADD COLUMN last_scrolled INTEGER DEFAULT 0")
    }

    if (oldVersion < LAST_PROFILE_FETCH) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN last_profile_fetch INTEGER DEFAULT 0")
    }

    if (oldVersion < SERVER_DELIVERED_TIMESTAMP) {
      db.execSQL("ALTER TABLE push ADD COLUMN server_delivered_timestamp INTEGER DEFAULT 0")
    }

    if (oldVersion < QUOTE_CLEANUP) {
      val query = (
        // language=sql
        """
          SELECT _data
          FROM (
            SELECT _data, MIN(quote) AS all_quotes
            FROM part 
            WHERE _data NOT NULL AND data_hash NOT NULL
            GROUP BY _data
          )
          WHERE all_quotes = 1
        """.trimIndent()
        )
      var count = 0
      db.rawQuery(query, null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val data: String = cursor.getString(cursor.getColumnIndexOrThrow("_data"))
          if (File(data).delete()) {
            val values = ContentValues().apply {
              putNull("_data")
              putNull("data_random")
              putNull("thumbnail")
              putNull("thumbnail_random")
              putNull("data_hash")
            }
            db.update("part", values, "_data = ?", arrayOf(data))
            count++
          } else {
            Log.w(TAG, "[QuoteCleanup] Failed to delete $data")
          }
        }
      }
      Log.i(TAG, "[QuoteCleanup] Cleaned up $count quotes.")
    }

    if (oldVersion < BORDERLESS) {
      db.execSQL("ALTER TABLE part ADD COLUMN borderless INTEGER DEFAULT 0")
    }

    if (oldVersion < REMAPPED_RECORDS) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE remapped_recipients (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            old_id INTEGER UNIQUE,
            new_id INTEGER
          )
        """.trimIndent()
      )
      db.execSQL(
        // language=sql
        """
          CREATE TABLE remapped_threads (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            old_id INTEGER UNIQUE,
            new_id INTEGER
          )
        """.trimIndent()
      )
    }

    if (oldVersion < MENTIONS) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE mention (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            thread_id INTEGER,
            message_id INTEGER,
            recipient_id INTEGER,
            range_start INTEGER,
            range_length INTEGER
          )
        """
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS mention_message_id_index ON mention (message_id)")
      db.execSQL("CREATE INDEX IF NOT EXISTS mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id);")
      db.execSQL("ALTER TABLE mms ADD COLUMN quote_mentions BLOB DEFAULT NULL")
      db.execSQL("ALTER TABLE mms ADD COLUMN mentions_self INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE recipient ADD COLUMN mention_setting INTEGER DEFAULT 0")
    }

    if (oldVersion < PINNED_CONVERSATIONS) {
      db.execSQL("ALTER TABLE thread ADD COLUMN pinned INTEGER DEFAULT 0")
      db.execSQL("CREATE INDEX IF NOT EXISTS thread_pinned_index ON thread (pinned)")
    }

    if (oldVersion < MENTION_GLOBAL_SETTING_MIGRATION) {
      val updateAlways = ContentValues()
      updateAlways.put("mention_setting", 0)
      db.update("recipient", updateAlways, "mention_setting = 1", null)
      val updateNever = ContentValues()
      updateNever.put("mention_setting", 1)
      db.update("recipient", updateNever, "mention_setting = 2", null)
    }

    if (oldVersion < UNKNOWN_STORAGE_FIELDS) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN storage_proto TEXT DEFAULT NULL")
    }

    if (oldVersion < STICKER_CONTENT_TYPE) {
      db.execSQL("ALTER TABLE sticker ADD COLUMN content_type TEXT DEFAULT NULL")
    }

    if (oldVersion < STICKER_EMOJI_IN_NOTIFICATIONS) {
      db.execSQL("ALTER TABLE part ADD COLUMN sticker_emoji TEXT DEFAULT NULL")
    }

    if (oldVersion < THUMBNAIL_CLEANUP) {
      var total = 0
      var deleted = 0
      db.rawQuery("SELECT thumbnail FROM part WHERE thumbnail NOT NULL", null).use { cursor ->
        if (cursor != null) {
          total = cursor.count
          Log.w(TAG, "Found $total thumbnails to delete.")
        }
        while (cursor != null && cursor.moveToNext()) {
          val file = File(CursorUtil.requireString(cursor, "thumbnail"))
          if (file.delete()) {
            deleted++
          } else {
            Log.w(TAG, "Failed to delete file! " + file.absolutePath)
          }
        }
      }
      Log.w(TAG, "Deleted $deleted/$total thumbnail files.")
    }

    if (oldVersion < STICKER_CONTENT_TYPE_CLEANUP) {
      val values = ContentValues().apply {
        put("ct", "image/webp")
      }
      val query = "sticker_id NOT NULL AND (ct IS NULL OR ct = '')"
      val rows = db.update("part", values, query, null)
      Log.i(TAG, "Updated $rows sticker attachment content types.")
    }

    if (oldVersion < MENTION_CLEANUP) {
      val selectMentionIdsNotInGroupsV2 = "select mention._id from mention left join thread on mention.thread_id = thread._id left join recipient on thread.recipient_ids = recipient._id where recipient.group_type != 3"
      db.delete("mention", "_id in ($selectMentionIdsNotInGroupsV2)", null)
      db.delete("mention", "message_id NOT IN (SELECT _id FROM mms) OR thread_id NOT IN (SELECT _id from thread)", null)

      val idsToDelete: MutableList<Long?> = LinkedList()
      db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id", null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val rangeStart: Int = CursorUtil.requireInt(cursor, "range_start")
          val rangeLength: Int = CursorUtil.requireInt(cursor, "range_length")
          val body: String? = CursorUtil.requireString(cursor, "body")
          if ((body == null) || body.isEmpty() || (rangeStart < 0) || (rangeLength < 0) || ((rangeStart + rangeLength) > body.length)) {
            idsToDelete.add(CursorUtil.requireLong(cursor, "_id"))
          }
        }
      }

      if (Util.hasItems(idsToDelete)) {
        val ids = TextUtils.join(",", idsToDelete)
        db.delete("mention", "_id in ($ids)", null)
      }
    }

    if (oldVersion < MENTION_CLEANUP_V2) {
      val selectMentionIdsWithMismatchingThreadIds = "select mention._id from mention left join mms on mention.message_id = mms._id where mention.thread_id != mms.thread_id"
      db.delete("mention", "_id in ($selectMentionIdsWithMismatchingThreadIds)", null)

      val idsToDelete: MutableList<Long?> = LinkedList()
      val mentionTuples: MutableSet<Triple<Long, Int, Int>> = HashSet()

      db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id order by mention._id desc", null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val mentionId: Long = CursorUtil.requireLong(cursor, "_id")
          val messageId: Long = CursorUtil.requireLong(cursor, "message_id")
          val rangeStart: Int = CursorUtil.requireInt(cursor, "range_start")
          val rangeLength: Int = CursorUtil.requireInt(cursor, "range_length")
          val body: String? = CursorUtil.requireString(cursor, "body")

          if ((body != null) && (rangeStart < body.length) && (body[rangeStart] != '\uFFFC')) {
            idsToDelete.add(mentionId)
          } else {
            val tuple: Triple<Long, Int, Int> = Triple(messageId, rangeStart, rangeLength)
            if (mentionTuples.contains(tuple)) {
              idsToDelete.add(mentionId)
            } else {
              mentionTuples.add(tuple)
            }
          }
        }

        if (Util.hasItems(idsToDelete)) {
          val ids: String = TextUtils.join(",", idsToDelete)
          db.delete("mention", "_id in ($ids)", null)
        }
      }
    }

    if (oldVersion < REACTION_CLEANUP) {
      val values = ContentValues().apply {
        putNull("reactions")
      }
      db.update("sms", values, "remote_deleted = ?", arrayOf("1"))
    }

    if (oldVersion < CAPABILITIES_REFACTOR) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN capabilities INTEGER DEFAULT 0")
      db.execSQL("UPDATE recipient SET capabilities = 1 WHERE gv2_capability = 1")
      db.execSQL("UPDATE recipient SET capabilities = 2 WHERE gv2_capability = -1")
    }

    if (oldVersion < GV1_MIGRATION) {
      db.execSQL("ALTER TABLE groups ADD COLUMN expected_v2_id TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE groups ADD COLUMN former_v1_members TEXT DEFAULT NULL")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON groups (expected_v2_id)")
      var count = 0
      db.rawQuery("SELECT * FROM groups WHERE group_id LIKE '__textsecure_group__!%' AND LENGTH(group_id) = 53", null).use { cursor ->
        while (cursor.moveToNext()) {
          val gv1: String = CursorUtil.requireString(cursor, "group_id")
          val gv2: String = GroupId.parseOrThrow(gv1).requireV1().deriveV2MigrationGroupId().toString()
          val values = ContentValues().apply {
            put("expected_v2_id", gv2)
          }
          count += db.update("groups", values, "group_id = ?", SqlUtil.buildArgs(gv1))
        }
      }
      Log.i(TAG, "Updated $count GV1 groups with expected GV2 IDs.")
    }

    if (oldVersion < NOTIFIED_TIMESTAMP) {
      db.execSQL("ALTER TABLE sms ADD COLUMN notified_timestamp INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE mms ADD COLUMN notified_timestamp INTEGER DEFAULT 0")
    }

    if (oldVersion < GV1_MIGRATION_LAST_SEEN) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN last_gv1_migrate_reminder INTEGER DEFAULT 0")
    }

    if (oldVersion < VIEWED_RECEIPTS) {
      db.execSQL("ALTER TABLE mms ADD COLUMN viewed_receipt_count INTEGER DEFAULT 0")
    }

    if (oldVersion < CLEAN_UP_GV1_IDS) {
      val deletableRecipients: MutableList<String> = LinkedList()
      db.rawQuery(
        // language=sql
        """
          SELECT _id, group_id 
          FROM recipient
          WHERE group_id NOT IN (SELECT group_id FROM groups)
            AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53
            AND (_id NOT IN (SELECT recipient_ids FROM thread) OR _id IN (SELECT recipient_ids FROM thread WHERE message_count = 0))
        """.trimIndent(),
        null
      ).use { cursor ->
        while (cursor.moveToNext()) {
          val recipientId: String = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
          val groupIdV1: String = cursor.getString(cursor.getColumnIndexOrThrow("group_id"))
          deletableRecipients.add(recipientId)
          Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with no or empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length))
        }
      }
      for (recipientId: String in deletableRecipients) {
        db.delete("recipient", "_id = ?", arrayOf(recipientId))
        Log.d(TAG, "Deleted recipient $recipientId")
      }
      val orphanedThreads: MutableList<String> = LinkedList()
      db.rawQuery("SELECT _id FROM thread WHERE message_count = 0 AND recipient_ids NOT IN (SELECT _id FROM recipient)", null).use { cursor ->
        while (cursor.moveToNext()) {
          orphanedThreads.add(cursor.getString(cursor.getColumnIndexOrThrow("_id")))
        }
      }
      for (orphanedThreadId: String in orphanedThreads) {
        db.delete("thread", "_id = ?", arrayOf(orphanedThreadId))
        Log.d(TAG, "Deleted orphaned thread $orphanedThreadId")
      }
      val remainingInvalidGV1Recipients: MutableList<String> = LinkedList()
      db.rawQuery(
        // language=sql
        """
          SELECT _id, group_id FROM recipient
          WHERE group_id NOT IN (SELECT group_id FROM groups)
            AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53
            AND _id IN (SELECT recipient_ids FROM thread)
        """.trimIndent(),
        null
      ).use { cursor ->
        while (cursor.moveToNext()) {
          val recipientId: String = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
          val groupIdV1: String = cursor.getString(cursor.getColumnIndexOrThrow("group_id"))
          remainingInvalidGV1Recipients.add(recipientId)
          Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with non-empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length))
        }
      }
      for (recipientId: String in remainingInvalidGV1Recipients) {
        val newId = "__textsecure_group__!" + Hex.toStringCondensed(Util.getSecretBytes(16))
        val values = ContentValues(1)
        values.put("group_id", newId)
        db.update("recipient", values, "_id = ?", arrayOf(recipientId))
        Log.d(TAG, String.format("Replaced group id on recipient %s now %s", recipientId, newId))
      }
    }

    if (oldVersion < GV1_MIGRATION_REFACTOR) {
      val values = ContentValues(1)
      values.putNull("former_v1_members")
      val count = db.update("groups", values, "former_v1_members NOT NULL", null)
      Log.i(TAG, "Cleared former_v1_members for $count rows")
    }

    if (oldVersion < CLEAR_PROFILE_KEY_CREDENTIALS) {
      val values = ContentValues(1)
      values.putNull("profile_key_credential")
      val count = db.update("recipient", values, "profile_key_credential NOT NULL", null)
      Log.i(TAG, "Cleared profile key credentials for $count rows")
    }

    if (oldVersion < LAST_RESET_SESSION_TIME) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN last_session_reset BLOB DEFAULT NULL")
    }

    if (oldVersion < WALLPAPER) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper BLOB DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper_file TEXT DEFAULT NULL")
    }

    if (oldVersion < ABOUT) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN about TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN about_emoji TEXT DEFAULT NULL")
    }

    if (oldVersion < SPLIT_SYSTEM_NAMES) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN system_family_name TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN system_given_name TEXT DEFAULT NULL")
      db.execSQL("UPDATE recipient SET system_given_name = system_display_name")
    }

    if (oldVersion < PAYMENTS) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE payments(
            _id INTEGER PRIMARY KEY,
            uuid TEXT DEFAULT NULL,
            recipient INTEGER DEFAULT 0,
            recipient_address TEXT DEFAULT NULL,
            timestamp INTEGER,
            note TEXT DEFAULT NULL,
            direction INTEGER,
            state INTEGER,
            failure_reason INTEGER,
            amount BLOB NOT NULL,
            fee BLOB NOT NULL,
            transaction_record BLOB DEFAULT NULL,
            receipt BLOB DEFAULT NULL,
            payment_metadata BLOB DEFAULT NULL,
            receipt_public_key TEXT DEFAULT NULL,
            block_index INTEGER DEFAULT 0,
            block_timestamp INTEGER DEFAULT 0,
            seen INTEGER,
            UNIQUE(uuid) ON CONFLICT ABORT
          )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_direction_index ON payments (timestamp, direction);")
      db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_index ON payments (timestamp);")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON payments (receipt_public_key);")
    }

    if (oldVersion < CLEAN_STORAGE_IDS) {
      val values = ContentValues()
      values.putNull("storage_service_key")
      val count = db.update("recipient", values, "storage_service_key NOT NULL AND ((phone NOT NULL AND INSTR(phone, '+') = 0) OR (group_id NOT NULL AND (LENGTH(group_id) != 85 and LENGTH(group_id) != 53)))", null)
      Log.i(TAG, "There were $count bad rows that had their storageID removed.")
    }

    if (oldVersion < MP4_GIF_SUPPORT) {
      db.execSQL("ALTER TABLE part ADD COLUMN video_gif INTEGER DEFAULT 0")
    }

    if (oldVersion < BLUR_AVATARS) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN extras BLOB DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN groups_in_common INTEGER DEFAULT 0")
      val secureOutgoingSms = "EXISTS(SELECT 1 FROM sms WHERE thread_id = t._id AND (type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))"
      val secureOutgoingMms = "EXISTS(SELECT 1 FROM mms WHERE thread_id = t._id AND (msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))"
      val selectIdsToUpdateProfileSharing = "SELECT r._id FROM recipient AS r INNER JOIN thread AS t ON r._id = t.recipient_ids WHERE profile_sharing = 0 AND ($secureOutgoingSms OR $secureOutgoingMms)"
      db.execSQL("UPDATE recipient SET profile_sharing = 1 WHERE _id IN ($selectIdsToUpdateProfileSharing)")
      val selectIdsWithGroupsInCommon =
        // language=sql
        """
          SELECT r._id FROM recipient AS r WHERE EXISTS (
            SELECT 1 
            FROM groups AS g 
            INNER JOIN recipient AS gr ON (g.recipient_id = gr._id AND gr.profile_sharing = 1) 
              WHERE g.active = 1 AND (g.members LIKE r._id || ',%' OR g.members LIKE '%,' || r._id || ',%' OR g.members LIKE '%,' || r._id)
          )
        """.trimIndent()

      db.execSQL("UPDATE recipient SET groups_in_common = 1 WHERE _id IN ($selectIdsWithGroupsInCommon)")
    }

    if (oldVersion < CLEAN_STORAGE_IDS_WITHOUT_INFO) {
      val values = ContentValues()
      values.putNull("storage_service_key")
      val count = db.update("recipient", values, "storage_service_key NOT NULL AND phone IS NULL AND uuid IS NULL AND group_id IS NULL", null)
      Log.i(TAG, "There were $count bad rows that had their storageID removed due to not having any other identifier.")
    }

    if (oldVersion < CLEAN_REACTION_NOTIFICATIONS) {
      val values = ContentValues(1)
      values.put("notified", 1)
      var count = 0
      count += db.update("sms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))", null)
      count += db.update("mms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))", null)
      Log.d(TAG, "Resetting notified for $count read incoming messages that were incorrectly flipped when receiving reactions")
      val smsIds: MutableList<Long> = ArrayList()
      db.query("sms", arrayOf("_id", "reactions", "notified_timestamp"), "notified = 0 AND reactions_unread = 1", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val reactions: ByteArray? = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"))
          val notifiedTimestamp: Long = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"))
          if (reactions == null) {
            continue
          }
          try {
            val hasReceiveLaterThanNotified: Boolean = ReactionList.ADAPTER.decode(reactions)
              .reactions
              .stream()
              .anyMatch { r: ReactionList.Reaction -> r.receivedTime > notifiedTimestamp }
            if (!hasReceiveLaterThanNotified) {
              smsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")))
            }
          } catch (e: IOException) {
            Log.e(TAG, e)
          }
        }
      }

      if (smsIds.size > 0) {
        Log.d(TAG, "Updating " + smsIds.size + " records in sms")
        db.execSQL("UPDATE sms SET reactions_last_seen = notified_timestamp WHERE _id in (" + Util.join(smsIds, ",") + ")")
      }

      val mmsIds: MutableList<Long> = ArrayList()
      db.query("mms", arrayOf("_id", "reactions", "notified_timestamp"), "notified = 0 AND reactions_unread = 1", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val reactions: ByteArray? = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"))
          val notifiedTimestamp: Long = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"))
          if (reactions == null) {
            continue
          }
          try {
            val hasReceiveLaterThanNotified: Boolean = ReactionList.ADAPTER.decode(reactions)
              .reactions
              .stream()
              .anyMatch { r: ReactionList.Reaction -> r.receivedTime > notifiedTimestamp }
            if (!hasReceiveLaterThanNotified) {
              mmsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")))
            }
          } catch (e: IOException) {
            Log.e(TAG, e)
          }
        }
      }
      if (mmsIds.size > 0) {
        Log.d(TAG, "Updating " + mmsIds.size + " records in mms")
        db.execSQL("UPDATE mms SET reactions_last_seen = notified_timestamp WHERE _id in (${Util.join(mmsIds, ",")})")
      }
    }

    if (oldVersion < STORAGE_SERVICE_REFACTOR) {
      val deleteCount: Int
      var insertCount: Int
      var updateCount: Int
      val dirtyCount: Int
      val deleteValues = ContentValues()

      deleteValues.putNull("storage_service_key")
      deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (dirty = 3 OR group_type = 1 OR (group_type = 0 AND registered = 2))", null)

      db.query("recipient", arrayOf("_id"), "storage_service_key IS NULL AND (dirty = 2 OR registered = 1)", null, null, null, null).use { cursor ->
        insertCount = cursor.count
        while (cursor.moveToNext()) {
          val insertValues = ContentValues().apply {
            put("storage_service_key", Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
          }
          val id: Long = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
          db.update("recipient", insertValues, "_id = ?", SqlUtil.buildArgs(id))
        }
      }

      db.query("recipient", arrayOf("_id"), "storage_service_key NOT NULL AND dirty = 1", null, null, null, null).use { cursor ->
        updateCount = cursor.count
        while (cursor.moveToNext()) {
          val updateValues = ContentValues().apply {
            put("storage_service_key", Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
          }
          val id: Long = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
          db.update("recipient", updateValues, "_id = ?", SqlUtil.buildArgs(id))
        }
      }

      val clearDirtyValues = ContentValues().apply {
        put("dirty", 0)
      }

      dirtyCount = db.update("recipient", clearDirtyValues, "dirty != 0", null)
      Log.d(TAG, String.format(Locale.US, "For storage service refactor migration, there were %d inserts, %d updated, and %d deletes. Cleared the dirty status on %d rows.", insertCount, updateCount, deleteCount, dirtyCount))
    }

    if (oldVersion < CLEAR_MMS_STORAGE_IDS) {
      val deleteValues = ContentValues().apply {
        putNull("storage_service_key")
      }
      val deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (group_type = 1 OR (group_type = 0 AND phone IS NULL AND uuid IS NULL))", null)
      Log.d(TAG, "Cleared storageIds from $deleteCount rows. They were either MMS groups or empty contacts.")
    }

    if (oldVersion < SERVER_GUID) {
      db.execSQL("ALTER TABLE sms ADD COLUMN server_guid TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE mms ADD COLUMN server_guid TEXT DEFAULT NULL")
    }

    if (oldVersion < CHAT_COLORS) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN chat_colors BLOB DEFAULT NULL")
      db.execSQL("ALTER TABLE recipient ADD COLUMN custom_chat_colors_id INTEGER DEFAULT 0")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE chat_colors (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_colors BLOB
          )
        """
      )
      val entrySet: Set<Map.Entry<MaterialColor, ChatColors>> = ChatColorsMapper.entrySet
      val where = "color = ? AND group_id is NULL"
      for (entry: Map.Entry<MaterialColor, ChatColors> in entrySet) {
        val whereArgs = SqlUtil.buildArgs(entry.key.serialize())
        val values = ContentValues(2)
        values.put("chat_colors", entry.value.serialize().encode())
        values.put("custom_chat_colors_id", entry.value.id.longValue)
        db.update("recipient", values, where, whereArgs)
      }
    }

    if (oldVersion < AVATAR_COLORS) {
      db.query("recipient", arrayOf("_id"), "color IS NULL", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val id: Long = cursor.getInt(cursor.getColumnIndexOrThrow("_id")).toLong()
          val values = ContentValues(1)
          values.put("color", AvatarColor.random().serialize())
          db.update("recipient", values, "_id = ?", arrayOf(id.toString()))
        }
      }
    }

    if (oldVersion < EMOJI_SEARCH) {
      // language=text
      db.execSQL("CREATE VIRTUAL TABLE emoji_search USING fts5(label, emoji UNINDEXED)")
    }

    if (oldVersion < SENDER_KEY && !SqlUtil.tableExists(db, "sender_keys")) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE sender_keys (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipient_id INTEGER NOT NULL,
            device INTEGER NOT NULL,
            distribution_id TEXT NOT NULL,
            record BLOB NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(recipient_id, device, distribution_id) ON CONFLICT REPLACE
          )
        """.trimIndent()
      )
      db.execSQL(
        // language=sql
        """
          CREATE TABLE sender_key_shared (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            distribution_id TEXT NOT NULL, 
            address TEXT NOT NULL,
            device INTEGER NOT NULL,
            UNIQUE(distribution_id, address, device) ON CONFLICT REPLACE
          )
        """.trimIndent()
      )
      db.execSQL(
        // language=sql
        """
          CREATE TABLE pending_retry_receipts (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            author TEXT NOT NULL,
            device INTEGER NOT NULL,
            sent_timestamp INTEGER NOT NULL,
            received_timestamp TEXT NOT NULL,
            thread_id INTEGER NOT NULL,
            UNIQUE(author, sent_timestamp) ON CONFLICT REPLACE
          );
        """.trimIndent()
      )
      db.execSQL("ALTER TABLE groups ADD COLUMN distribution_id TEXT DEFAULT NULL")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS group_distribution_id_index ON groups (distribution_id)")
      db.query("groups", arrayOf("group_id"), "LENGTH(group_id) = 85", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val groupId: String = cursor.getString(cursor.getColumnIndexOrThrow("group_id"))
          val values = ContentValues().apply {
            put("distribution_id", DistributionId.create().toString())
          }
          db.update("groups", values, "group_id = ?", arrayOf(groupId))
        }
      }
    }

    if (oldVersion < MESSAGE_DUPE_INDEX) {
      db.execSQL("DROP INDEX sms_date_sent_index")
      db.execSQL("CREATE INDEX sms_date_sent_index on sms(date_sent, address, thread_id)")
      db.execSQL("DROP INDEX mms_date_sent_index")
      db.execSQL("CREATE INDEX mms_date_sent_index on mms(date, address, thread_id)")
    }

    if (oldVersion < MESSAGE_LOG) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE message_send_log (
            _id INTEGER PRIMARY KEY,
            date_sent INTEGER NOT NULL,
            content BLOB NOT NULL,
            related_message_id INTEGER DEFAULT -1,
            is_related_message_mms INTEGER DEFAULT 0, 
            content_hint INTEGER NOT NULL,
            group_id BLOB DEFAULT NULL
          )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX message_log_date_sent_index ON message_send_log (date_sent)")
      db.execSQL("CREATE INDEX message_log_related_message_index ON message_send_log (related_message_id, is_related_message_mms)")
      db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 0; END")
      db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 1; END")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE message_send_log_recipients (
            _id INTEGER PRIMARY KEY,
            message_send_log_id INTEGER NOT NULL REFERENCES message_send_log (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL,
            device INTEGER NOT NULL
          )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX message_send_log_recipients_recipient_index ON message_send_log_recipients (recipient_id, device)")
    }

    if (oldVersion < MESSAGE_LOG_2) {
      db.execSQL("DROP TABLE message_send_log")
      db.execSQL("DROP INDEX IF EXISTS message_log_date_sent_index")
      db.execSQL("DROP INDEX IF EXISTS message_log_related_message_index")
      db.execSQL("DROP TRIGGER msl_sms_delete")
      db.execSQL("DROP TRIGGER msl_mms_delete")
      db.execSQL("DROP TABLE message_send_log_recipients")
      db.execSQL("DROP INDEX IF EXISTS message_send_log_recipients_recipient_index")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE msl_payload (
            _id INTEGER PRIMARY KEY,
            date_sent INTEGER NOT NULL,
            content BLOB NOT NULL,
            content_hint INTEGER NOT NULL
          )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX msl_payload_date_sent_index ON msl_payload (date_sent)")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE msl_recipient (
            _id INTEGER PRIMARY KEY,
            payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL,
            device INTEGER NOT NULL
          )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX msl_recipient_recipient_index ON msl_recipient (recipient_id, device, payload_id)")
      db.execSQL("CREATE INDEX msl_recipient_payload_index ON msl_recipient (payload_id)")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE msl_message (
            _id INTEGER PRIMARY KEY, 
            payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE,
            message_id INTEGER NOT NULL,
            is_mms INTEGER NOT NULL
          )
        """
      )

      db.execSQL("CREATE INDEX msl_message_message_index ON msl_message (message_id, is_mms, payload_id)")
      db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END")
      db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END")
      db.execSQL("CREATE TRIGGER msl_attachment_delete AFTER DELETE ON part BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old.mid AND is_mms = 1); END")
    }

    if (oldVersion < ABANDONED_MESSAGE_CLEANUP) {
      val start = System.currentTimeMillis()
      val smsDeleteCount = db.delete("sms", "thread_id NOT IN (SELECT _id FROM thread)", null)
      val mmsDeleteCount = db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null)
      Log.i(TAG, "Deleted " + smsDeleteCount + " sms and " + mmsDeleteCount + " mms in " + (System.currentTimeMillis() - start) + " ms")
    }

    if (oldVersion < THREAD_AUTOINCREMENT) {
      val stopwatch = Stopwatch("thread-autoincrement")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE thread_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            date INTEGER DEFAULT 0,
            thread_recipient_id INTEGER,
            message_count INTEGER DEFAULT 0,
            snippet TEXT,
            snippet_charset INTEGER DEFAULT 0,
            snippet_type INTEGER DEFAULT 0,
            snippet_uri TEXT DEFAULT NULL,
            snippet_content_type INTEGER DEFAULT NULL,
            snippet_extras TEXT DEFAULT NULL,
            read INTEGER DEFAULT 1,
            type INTEGER DEFAULT 0,
            error INTEGER DEFAULT 0,
            archived INTEGER DEFAULT 0,
            status INTEGER DEFAULT 0,
            expires_in INTEGER DEFAULT 0,
            last_seen INTEGER DEFAULT 0,
            has_sent INTEGER DEFAULT 0,
            delivery_receipt_count INTEGER DEFAULT 0,
            read_receipt_count INTEGER DEFAULT 0,
            unread_count INTEGER DEFAULT 0,
            last_scrolled INTEGER DEFAULT 0,
            pinned INTEGER DEFAULT 0
          )
        """.trimIndent()
      )
      stopwatch.split("table-create")

      db.execSQL(
        // language=sql
        """
          INSERT INTO thread_tmp 
          SELECT 
            _id,
            date,
            recipient_ids,
            message_count,
            snippet,
            snippet_cs,
            snippet_type,
            snippet_uri,
            snippet_content_type,
            snippet_extras,
            read,
            type,
            error,
            archived,
            status,
            expires_in,
            last_seen,
            has_sent,
            delivery_receipt_count,
            read_receipt_count,
            unread_count,
            last_scrolled,
            pinned
          FROM thread
        """.trimIndent()
      )
      stopwatch.split("table-copy")

      db.execSQL("DROP TABLE thread")
      db.execSQL("ALTER TABLE thread_tmp RENAME TO thread")
      stopwatch.split("table-rename")

      db.execSQL("CREATE INDEX thread_recipient_id_index ON thread (thread_recipient_id)")
      db.execSQL("CREATE INDEX archived_count_index ON thread (archived, message_count)")
      db.execSQL("CREATE INDEX thread_pinned_index ON thread (pinned)")
      stopwatch.split("indexes")

      db.execSQL("DELETE FROM remapped_threads")
      stopwatch.split("delete-remap")

      stopwatch.stop(TAG)
    }

    if (oldVersion < MMS_AUTOINCREMENT) {
      val mmsStopwatch = Stopwatch("mms-autoincrement")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE mms_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            thread_id INTEGER,
            date INTEGER,
            date_received INTEGER,
            date_server INTEGER DEFAULT -1,
            msg_box INTEGER,
            read INTEGER DEFAULT 0,
            body TEXT,
            part_count INTEGER,
            ct_l TEXT,
            address INTEGER,
            address_device_id INTEGER,
            exp INTEGER,
            m_type INTEGER,
            m_size INTEGER,
            st INTEGER,
            tr_id TEXT,
            delivery_receipt_count INTEGER DEFAULT 0,
            mismatched_identities TEXT DEFAULT NULL,
            network_failures TEXT DEFAULT NULL,
            subscription_id INTEGER DEFAULT -1,
            expires_in INTEGER DEFAULT 0,
            expire_started INTEGER DEFAULT 0,
            notified INTEGER DEFAULT 0,
            read_receipt_count INTEGER DEFAULT 0,
            quote_id INTEGER DEFAULT 0,
            quote_author TEXT,
            quote_body TEXT,
            quote_attachment INTEGER DEFAULT -1,
            quote_missing INTEGER DEFAULT 0,
            quote_mentions BLOB DEFAULT NULL,
            shared_contacts TEXT,
            unidentified INTEGER DEFAULT 0,
            previews TEXT,
            reveal_duration INTEGER DEFAULT 0,
            reactions BLOB DEFAULT NULL,
            reactions_unread INTEGER DEFAULT 0,
            reactions_last_seen INTEGER DEFAULT -1,
            remote_deleted INTEGER DEFAULT 0,
            mentions_self INTEGER DEFAULT 0,
            notified_timestamp INTEGER DEFAULT 0,
            viewed_receipt_count INTEGER DEFAULT 0,
            server_guid TEXT DEFAULT NULL
          );
        """.trimIndent()
      )
      mmsStopwatch.split("table-create")

      db.execSQL(
        // language=sql
        """
          INSERT INTO mms_tmp 
          SELECT 
            _id,
            thread_id,
            date,
            date_received,
            date_server,
            msg_box,
            read,
            body,
            part_count,
            ct_l,
            address,
            address_device_id,
            exp,
            m_type,
            m_size,
            st,
            tr_id,
            delivery_receipt_count,
            mismatched_identities,
            network_failures,
            subscription_id,
            expires_in,
            expire_started,
            notified,
            read_receipt_count,
            quote_id,
            quote_author,
            quote_body,
            quote_attachment,
            quote_missing,
            quote_mentions,
            shared_contacts,
            unidentified,
            previews,
            reveal_duration,
            reactions,
            reactions_unread,
            reactions_last_seen,
            remote_deleted,
            mentions_self,
            notified_timestamp,
            viewed_receipt_count,
            server_guid
          FROM mms
        """.trimIndent()
      )
      mmsStopwatch.split("table-copy")

      db.execSQL("DROP TABLE mms")
      db.execSQL("ALTER TABLE mms_tmp RENAME TO mms")
      mmsStopwatch.split("table-rename")

      db.execSQL("CREATE INDEX mms_read_and_notified_and_thread_id_index ON mms(read, notified, thread_id)")
      db.execSQL("CREATE INDEX mms_message_box_index ON mms (msg_box)")
      db.execSQL("CREATE INDEX mms_date_sent_index ON mms (date, address, thread_id)")
      db.execSQL("CREATE INDEX mms_date_server_index ON mms (date_server)")
      db.execSQL("CREATE INDEX mms_thread_date_index ON mms (thread_id, date_received)")
      db.execSQL("CREATE INDEX mms_reactions_unread_index ON mms (reactions_unread)")
      mmsStopwatch.split("indexes")

      db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END")
      db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END")
      // language=text
      db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END")
      db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END")
      mmsStopwatch.split("triggers")

      mmsStopwatch.stop(TAG)

      val smsStopwatch = Stopwatch("sms-autoincrement")
      db.execSQL(
        // language=sql
        """
          CREATE TABLE sms_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            thread_id INTEGER,
            address INTEGER,
            address_device_id INTEGER DEFAULT 1,
            person INTEGER,
            date INTEGER,
            date_sent INTEGER,
            date_server INTEGER DEFAULT -1,
            protocol INTEGER,
            read INTEGER DEFAULT 0,
            status INTEGER DEFAULT -1,
            type INTEGER,
            reply_path_present INTEGER,
            delivery_receipt_count INTEGER DEFAULT 0,
            subject TEXT,
            body TEXT,
            mismatched_identities TEXT DEFAULT NULL,
            service_center TEXT,
            subscription_id INTEGER DEFAULT -1,
            expires_in INTEGER DEFAULT 0,
            expire_started INTEGER DEFAULT 0,
            notified DEFAULT 0,
            read_receipt_count INTEGER DEFAULT 0,
            unidentified INTEGER DEFAULT 0,
            reactions BLOB DEFAULT NULL,
            reactions_unread INTEGER DEFAULT 0,
            reactions_last_seen INTEGER DEFAULT -1,
            remote_deleted INTEGER DEFAULT 0,
            notified_timestamp INTEGER DEFAULT 0,
            server_guid TEXT DEFAULT NULL
          )
        """.trimIndent()
      )
      smsStopwatch.split("table-create")
      db.execSQL(
        // language=sql
        """
          INSERT INTO sms_tmp 
          SELECT 
            _id,
            thread_id,
            address,
            address_device_id,
            person,
            date,
            date_sent,
            date_server ,
            protocol,
            read,
            status ,
            type,
            reply_path_present,
            delivery_receipt_count,
            subject,
            body,
            mismatched_identities,
            service_center,
            subscription_id ,
            expires_in,
            expire_started,
            notified,
            read_receipt_count,
            unidentified,
            reactions BLOB,
            reactions_unread,
            reactions_last_seen ,
            remote_deleted,
            notified_timestamp,
            server_guid
          FROM sms
        """.trimIndent()
      )
      smsStopwatch.split("table-copy")

      db.execSQL("DROP TABLE sms")
      db.execSQL("ALTER TABLE sms_tmp RENAME TO sms")
      smsStopwatch.split("table-rename")

      db.execSQL("CREATE INDEX sms_read_and_notified_and_thread_id_index ON sms(read, notified, thread_id)")
      db.execSQL("CREATE INDEX sms_type_index ON sms (type)")
      db.execSQL("CREATE INDEX sms_date_sent_index ON sms (date_sent, address, thread_id)")
      db.execSQL("CREATE INDEX sms_date_server_index ON sms (date_server)")
      db.execSQL("CREATE INDEX sms_thread_date_index ON sms (thread_id, date)")
      db.execSQL("CREATE INDEX sms_reactions_unread_index ON sms (reactions_unread)")
      smsStopwatch.split("indexes")

      db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END;")
      db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END;")
      // language=text
      db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id); END;")
      db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END")
      smsStopwatch.split("triggers")

      smsStopwatch.stop(TAG)
    }

    if (oldVersion < ABANDONED_ATTACHMENT_CLEANUP) {
      db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null)
    }

    if (oldVersion < AVATAR_PICKER) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE avatar_picker (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            last_used INTEGER DEFAULT 0,
            group_id TEXT DEFAULT NULL,
            avatar BLOB NOT NULL
          )
        """.trimIndent()
      )
      db.query("recipient", arrayOf("_id"), "color IS NULL", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val id: Long = cursor.getInt(cursor.getColumnIndexOrThrow("_id")).toLong()
          val values = ContentValues(1).apply {
            put("color", AvatarColor.random().serialize())
          }
          db.update("recipient", values, "_id = ?", arrayOf(id.toString()))
        }
      }
    }

    if (oldVersion < THREAD_CLEANUP) {
      db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null)
      db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null)
    }

    if (oldVersion < SESSION_MIGRATION) {
      val start = System.currentTimeMillis()
      db.execSQL(
        // language=sql
        """
          CREATE TABLE sessions_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            address TEXT NOT NULL,
            device INTEGER NOT NULL,
            record BLOB NOT NULL,
            UNIQUE(address, device))
        """.trimIndent()
      )
      db.execSQL(
        // language=sql
        """
          INSERT INTO sessions_tmp (address, device, record) 
          SELECT 
            COALESCE(recipient.uuid, recipient.phone) AS new_address, 
            sessions.device, 
            sessions.record 
          FROM sessions INNER JOIN recipient ON sessions.address = recipient._id 
          WHERE new_address NOT NULL
        """.trimIndent()
      )
      db.execSQL("DROP TABLE sessions")
      db.execSQL("ALTER TABLE sessions_tmp RENAME TO sessions")
      Log.d(TAG, "Session migration took " + (System.currentTimeMillis() - start) + " ms")
    }

    if (oldVersion < IDENTITY_MIGRATION) {
      val start = System.currentTimeMillis()
      db.execSQL(
        // language=sql
        """
          CREATE TABLE identities_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            address TEXT UNIQUE NOT NULL,
            identity_key TEXT,
            first_use INTEGER DEFAULT 0,
            timestamp INTEGER DEFAULT 0,
            verified INTEGER DEFAULT 0,
            nonblocking_approval INTEGER DEFAULT 0)
        """.trimIndent()
      )
      db.execSQL(
        // language=sql
        """
          INSERT INTO identities_tmp (address, identity_key, first_use, timestamp, verified, nonblocking_approval)
          SELECT 
            COALESCE(recipient.uuid, recipient.phone) AS new_address,
            identities.key,
            identities.first_use,
            identities.timestamp,
            identities.verified,
            identities.nonblocking_approval
          FROM identities INNER JOIN recipient ON identities.address = recipient._id
          WHERE new_address NOT NULL
        """.trimIndent()
      )
      db.execSQL("DROP TABLE identities")
      db.execSQL("ALTER TABLE identities_tmp RENAME TO identities")
      Log.d(TAG, "Identity migration took " + (System.currentTimeMillis() - start) + " ms")
    }

    if (oldVersion < GROUP_CALL_RING_TABLE) {
      db.execSQL("CREATE TABLE group_call_ring (_id INTEGER PRIMARY KEY, ring_id INTEGER UNIQUE, date_received INTEGER, ring_state INTEGER)")
      db.execSQL("CREATE INDEX date_received_index on group_call_ring (date_received)")
    }

    if (oldVersion < CLEANUP_SESSION_MIGRATION) {
      val sessionCount = db.delete("sessions", "address LIKE '+%'", null)
      Log.i(TAG, "Cleaned up $sessionCount sessions.")
      val storageValues = ContentValues()
      storageValues.putNull("storage_service_key")
      val storageCount = db.update("recipient", storageValues, "storage_service_key NOT NULL AND group_id IS NULL AND uuid IS NULL", null)
      Log.i(TAG, "Cleaned up $storageCount storageIds.")
    }

    if (oldVersion < RECEIPT_TIMESTAMP) {
      db.execSQL("ALTER TABLE sms ADD COLUMN receipt_timestamp INTEGER DEFAULT -1")
      db.execSQL("ALTER TABLE mms ADD COLUMN receipt_timestamp INTEGER DEFAULT -1")
    }

    if (oldVersion < BADGES) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN badges BLOB DEFAULT NULL")
    }

    if (oldVersion < SENDER_KEY_UUID) {
      val start = System.currentTimeMillis()
      db.execSQL(
        // language=sql
        """
          CREATE TABLE sender_keys_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            address TEXT NOT NULL,
            device INTEGER NOT NULL,
            distribution_id TEXT NOT NULL,
            record BLOB NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(address, device, distribution_id) ON CONFLICT REPLACE
          )
        """.trimIndent()
      )

      db.execSQL(
        // language=sql
        """
          INSERT INTO sender_keys_tmp (address, device, distribution_id, record, created_at)
          SELECT 
            recipient.uuid AS new_address,
            sender_keys.device,
            sender_keys.distribution_id,
            sender_keys.record,
            sender_keys.created_at
          FROM sender_keys INNER JOIN recipient ON sender_keys.recipient_id = recipient._id
          WHERE new_address NOT NULL
        """.trimIndent()
      )

      db.execSQL("DROP TABLE sender_keys")
      db.execSQL("ALTER TABLE sender_keys_tmp RENAME TO sender_keys")

      Log.d(TAG, "Sender key migration took " + (System.currentTimeMillis() - start) + " ms")
    }

    if (oldVersion < SENDER_KEY_SHARED_TIMESTAMP) {
      db.execSQL("ALTER TABLE sender_key_shared ADD COLUMN timestamp INTEGER DEFAULT 0")
    }

    if (oldVersion < REACTION_REFACTOR) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE reaction (
            _id INTEGER PRIMARY KEY,
            message_id INTEGER NOT NULL,
            is_mms INTEGER NOT NULL,
            author_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
            emoji TEXT NOT NULL,
            date_sent INTEGER NOT NULL,
            date_received INTEGER NOT NULL,
            UNIQUE(message_id, is_mms, author_id) ON CONFLICT REPLACE
          )
        """.trimIndent()
      )

      db.rawQuery("SELECT _id, reactions FROM sms WHERE reactions NOT NULL", null).use { cursor ->
        while (cursor.moveToNext()) {
          migrateReaction(db, cursor, false)
        }
      }

      db.rawQuery("SELECT _id, reactions FROM mms WHERE reactions NOT NULL", null).use { cursor ->
        while (cursor.moveToNext()) {
          migrateReaction(db, cursor, true)
        }
      }

      db.execSQL("UPDATE reaction SET author_id = IFNULL((SELECT new_id FROM remapped_recipients WHERE author_id = old_id), author_id)")
      db.execSQL("CREATE TRIGGER reactions_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM reaction WHERE message_id = old._id AND is_mms = 0; END")
      db.execSQL("CREATE TRIGGER reactions_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM reaction WHERE message_id = old._id AND is_mms = 0; END")
      db.execSQL("UPDATE sms SET reactions = NULL WHERE reactions NOT NULL")
      db.execSQL("UPDATE mms SET reactions = NULL WHERE reactions NOT NULL")
    }

    if (oldVersion < PNI) {
      db.execSQL("ALTER TABLE recipient ADD COLUMN pni TEXT DEFAULT NULL")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS recipient_pni_index ON recipient (pni)")
    }

    if (oldVersion < NOTIFICATION_PROFILES) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE notification_profile (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, 
            name TEXT NOT NULL UNIQUE,
            emoji TEXT NOT NULL,
            color TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            allow_all_calls INTEGER NOT NULL DEFAULT 0,
            allow_all_mentions INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent()
      )

      db.execSQL(
        // language=sql
        """
          CREATE TABLE notification_profile_schedule (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE,
            enabled INTEGER NOT NULL DEFAULT 0,
            start INTEGER NOT NULL,
            end INTEGER NOT NULL,
            days_enabled TEXT NOT NULL
          )
        """.trimIndent()
      )

      db.execSQL(
        // language=sql
        """
          CREATE TABLE notification_profile_allowed_members (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL,
            UNIQUE(notification_profile_id, recipient_id) ON CONFLICT REPLACE)
        """.trimIndent()
      )

      db.execSQL("CREATE INDEX notification_profile_schedule_profile_index ON notification_profile_schedule (notification_profile_id)")
      db.execSQL("CREATE INDEX notification_profile_allowed_members_profile_index ON notification_profile_allowed_members (notification_profile_id)")
    }

    if (oldVersion < NOTIFICATION_PROFILES_END_FIX) {
      db.execSQL(
        // language=sql
        """
          UPDATE notification_profile_schedule SET end = 2400 WHERE end = 0
        """.trimIndent()
      )
    }

    if (oldVersion < REACTION_BACKUP_CLEANUP) {
      db.execSQL(
        // language=sql
        """
          DELETE FROM reaction
          WHERE
            (is_mms = 0 AND message_id NOT IN (SELECT _id FROM sms))
            OR
            (is_mms = 1 AND message_id NOT IN (SELECT _id FROM mms))
        """.trimIndent()
      )
    }

    if (oldVersion < REACTION_REMOTE_DELETE_CLEANUP) {
      db.execSQL(
        // language=sql
        """
          DELETE FROM reaction
          WHERE
            (is_mms = 0 AND message_id IN (SELECT _id from sms WHERE remote_deleted = 1))
            OR
            (is_mms = 1 AND message_id IN (SELECT _id from mms WHERE remote_deleted = 1))
        """.trimIndent()
      )
    }

    if (oldVersion < PNI_CLEANUP) {
      db.execSQL("UPDATE recipient SET pni = NULL WHERE phone IS NULL")
    }

    if (oldVersion < MESSAGE_RANGES) {
      db.execSQL("ALTER TABLE mms ADD COLUMN ranges BLOB DEFAULT NULL")
    }

    if (oldVersion < REACTION_TRIGGER_FIX) {
      db.execSQL("DROP TRIGGER reactions_mms_delete")
      db.execSQL("CREATE TRIGGER reactions_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM reaction WHERE message_id = old._id AND is_mms = 1; END")

      db.execSQL(
        // language=sql
        """
          DELETE FROM reaction
          WHERE
            (is_mms = 0 AND message_id NOT IN (SELECT _id from sms))
            OR
            (is_mms = 1 AND message_id NOT IN (SELECT _id from mms))
        """.trimIndent()
      )
    }

    if (oldVersion < PNI_STORES) {
      val localAci: ACI? = getLocalAci(context)

      // One-Time Prekeys
      db.execSQL(
        """
        CREATE TABLE one_time_prekeys_tmp (
          _id INTEGER PRIMARY KEY,
          account_id TEXT NOT NULL,
          key_id INTEGER,
          public_key TEXT NOT NULL,
          private_key TEXT NOT NULL,
          UNIQUE(account_id, key_id)
        )
        """.trimIndent()
      )

      if (localAci != null) {
        db.execSQL(
          """
          INSERT INTO one_time_prekeys_tmp (account_id, key_id, public_key, private_key)
          SELECT
            '$localAci' AS account_id,
            one_time_prekeys.key_id,
            one_time_prekeys.public_key,
            one_time_prekeys.private_key
          FROM one_time_prekeys
          """.trimIndent()
        )
      } else {
        Log.w(TAG, "No local ACI set. Not migrating any existing one-time prekeys.")
      }

      db.execSQL("DROP TABLE one_time_prekeys")
      db.execSQL("ALTER TABLE one_time_prekeys_tmp RENAME TO one_time_prekeys")

      // Signed Prekeys
      db.execSQL(
        """
        CREATE TABLE signed_prekeys_tmp (
          _id INTEGER PRIMARY KEY,
          account_id TEXT NOT NULL,
          key_id INTEGER,
          public_key TEXT NOT NULL,
          private_key TEXT NOT NULL,
          signature TEXT NOT NULL,
          timestamp INTEGER DEFAULT 0,
          UNIQUE(account_id, key_id)
        )
        """.trimIndent()
      )

      if (localAci != null) {
        db.execSQL(
          """
          INSERT INTO signed_prekeys_tmp (account_id, key_id, public_key, private_key, signature, timestamp)
          SELECT
            '$localAci' AS account_id,
            signed_prekeys.key_id,
            signed_prekeys.public_key,
            signed_prekeys.private_key,
            signed_prekeys.signature,
            signed_prekeys.timestamp
          FROM signed_prekeys
          """.trimIndent()
        )
      } else {
        Log.w(TAG, "No local ACI set. Not migrating any existing signed prekeys.")
      }

      db.execSQL("DROP TABLE signed_prekeys")
      db.execSQL("ALTER TABLE signed_prekeys_tmp RENAME TO signed_prekeys")

      // Sessions
      db.execSQL(
        """
        CREATE TABLE sessions_tmp (
          _id INTEGER PRIMARY KEY AUTOINCREMENT,
          account_id TEXT NOT NULL,
          address TEXT NOT NULL,
          device INTEGER NOT NULL,
          record BLOB NOT NULL,
          UNIQUE(account_id, address, device)
        )
        """.trimIndent()
      )

      if (localAci != null) {
        db.execSQL(
          """
          INSERT INTO sessions_tmp (account_id, address, device, record)
          SELECT
            '$localAci' AS account_id,
            sessions.address,
            sessions.device,
            sessions.record
          FROM sessions
          """.trimIndent()
        )
      } else {
        Log.w(TAG, "No local ACI set. Not migrating any existing sessions.")
      }

      db.execSQL("DROP TABLE sessions")
      db.execSQL("ALTER TABLE sessions_tmp RENAME TO sessions")
    }

    if (oldVersion < DONATION_RECEIPTS) {
      db.execSQL(
        // language=sql
        """
          CREATE TABLE donation_receipt (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            receipt_type TEXT NOT NULL,
            receipt_date INTEGER NOT NULL,
            amount TEXT NOT NULL,
            currency TEXT NOT NULL,
            subscription_level INTEGER NOT NULL
          )
        """.trimIndent()
      )

      db.execSQL("CREATE INDEX IF NOT EXISTS donation_receipt_type_index ON donation_receipt (receipt_type);")
      db.execSQL("CREATE INDEX IF NOT EXISTS donation_receipt_date_index ON donation_receipt (receipt_date);")
    }

    if (oldVersion < STORIES) {
      db.execSQL("ALTER TABLE mms ADD COLUMN is_story INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE mms ADD COLUMN parent_story_id INTEGER DEFAULT 0")
      db.execSQL("CREATE INDEX IF NOT EXISTS mms_is_story_index ON mms (is_story)")
      db.execSQL("CREATE INDEX IF NOT EXISTS mms_parent_story_id_index ON mms (parent_story_id)")

      db.execSQL("ALTER TABLE recipient ADD COLUMN distribution_list_id INTEGER DEFAULT NULL")

      db.execSQL(
        // language=sql
        """
            CREATE TABLE distribution_list (
              _id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT UNIQUE NOT NULL,
              distribution_id TEXT UNIQUE NOT NULL,
              recipient_id INTEGER UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE
            )
        """.trimIndent()
      )

      db.execSQL(
        // language=sql
        """
            CREATE TABLE distribution_list_member (
              _id INTEGER PRIMARY KEY AUTOINCREMENT,
              list_id INTEGER NOT NULL REFERENCES distribution_list (_id) ON DELETE CASCADE,
              recipient_id INTEGER NOT NULL,
              UNIQUE(list_id, recipient_id) ON CONFLICT IGNORE
            )
        """.trimIndent()
      )

      val recipientId = db.insert(
        "recipient",
        null,
        contentValuesOf(
          "distribution_list_id" to 1L,
          "storage_service_key" to Base64.encodeWithPadding(StorageSyncHelper.generateKey()),
          "profile_sharing" to 1
        )
      )

      val listUUID = UUID.randomUUID().toString()
      db.insert(
        "distribution_list",
        null,
        contentValuesOf(
          "_id" to 1L,
          "name" to listUUID,
          "distribution_id" to listUUID,
          "recipient_id" to recipientId
        )
      )
    }

    if (oldVersion < ALLOW_STORY_REPLIES) {
      db.execSQL("ALTER TABLE distribution_list ADD COLUMN allows_replies INTEGER DEFAULT 1")
    }

    if (oldVersion < GROUP_STORIES) {
      db.execSQL("ALTER TABLE groups ADD COLUMN display_as_story INTEGER DEFAULT 0")
    }

    if (oldVersion < MMS_COUNT_INDEX) {
      db.execSQL("CREATE INDEX IF NOT EXISTS mms_thread_story_parent_story_index ON mms (thread_id, date_received, is_story, parent_story_id)")
    }

    if (oldVersion < STORY_SENDS) {
      db.execSQL(
        """
          CREATE TABLE story_sends (
            _id INTEGER PRIMARY KEY,
            message_id INTEGER NOT NULL REFERENCES mms (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
            sent_timestamp INTEGER NOT NULL,
            allows_replies INTEGER NOT NULL
          )
        """.trimIndent()
      )

      db.execSQL("CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON story_sends (recipient_id, sent_timestamp, allows_replies)")
    }

    if (oldVersion < STORY_TYPE_AND_DISTRIBUTION) {
      db.execSQL("ALTER TABLE distribution_list ADD COLUMN deletion_timestamp INTEGER DEFAULT 0")

      db.execSQL(
        """
        UPDATE recipient
        SET group_type = 4
        WHERE distribution_list_id IS NOT NULL
        """.trimIndent()
      )

      db.execSQL(
        """
        UPDATE distribution_list
        SET name = '00000000-0000-0000-0000-000000000000',
            distribution_id = '00000000-0000-0000-0000-000000000000'
        WHERE _id = 1
        """.trimIndent()
      )
    }

    if (oldVersion < CLEAN_DELETED_DISTRIBUTION_LISTS) {
      db.execSQL(
        """
          UPDATE recipient
          SET storage_service_key = NULL
          WHERE distribution_list_id IS NOT NULL AND NOT EXISTS(SELECT _id from distribution_list WHERE _id = distribution_list_id)
        """.trimIndent()
      )
    }

    if (oldVersion < REMOVE_KNOWN_UNKNOWNS) {
      val count: Int = db.delete("storage_key", "type <= ?", SqlUtil.buildArgs(4))
      Log.i(TAG, "Cleaned up $count invalid unknown records.")
    }

    if (oldVersion < CDS_V2) {
      db.execSQL("CREATE INDEX IF NOT EXISTS recipient_service_id_profile_key ON recipient (uuid, profile_key) WHERE uuid NOT NULL AND profile_key NOT NULL")
      db.execSQL(
        """
        CREATE TABLE cds (
          _id INTEGER PRIMARY KEY,
          e164 TEXT NOT NULL UNIQUE ON CONFLICT IGNORE,
          last_seen_at INTEGER DEFAULT 0
        )
      """
      )
    }

    if (oldVersion < GROUP_SERVICE_ID) {
      db.execSQL("ALTER TABLE groups ADD COLUMN auth_service_id TEXT DEFAULT NULL")
    }

    if (oldVersion < QUOTE_TYPE) {
      db.execSQL("ALTER TABLE mms ADD COLUMN quote_type INTEGER DEFAULT 0")
    }

    if (oldVersion < STORY_SYNCS) {
      db.execSQL("ALTER TABLE distribution_list ADD COLUMN is_unknown INTEGER DEFAULT 0")

      db.execSQL(
        """
          CREATE TABLE story_sends_tmp (
            _id INTEGER PRIMARY KEY,
            message_id INTEGER NOT NULL REFERENCES mms (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
            sent_timestamp INTEGER NOT NULL,
            allows_replies INTEGER NOT NULL,
            distribution_id TEXT NOT NULL REFERENCES distribution_list (distribution_id) ON DELETE CASCADE
          )
        """.trimIndent()
      )

      db.execSQL(
        """
          INSERT INTO story_sends_tmp (_id, message_id, recipient_id, sent_timestamp, allows_replies, distribution_id)
              SELECT story_sends._id, story_sends.message_id, story_sends.recipient_id, story_sends.sent_timestamp, story_sends.allows_replies, distribution_list.distribution_id
              FROM story_sends
              INNER JOIN mms ON story_sends.message_id = mms._id
              INNER JOIN distribution_list ON distribution_list.recipient_id = mms.address
        """.trimIndent()
      )

      db.execSQL("DROP TABLE story_sends")
      db.execSQL("DROP INDEX IF EXISTS story_sends_recipient_id_sent_timestamp_allows_replies_index")

      db.execSQL("ALTER TABLE story_sends_tmp RENAME TO story_sends")
      db.execSQL("CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON story_sends (recipient_id, sent_timestamp, allows_replies)")
    }

    if (oldVersion < GROUP_STORY_NOTIFICATIONS) {
      db.execSQL("UPDATE mms SET read = 1 WHERE parent_story_id > 0")
    }

    if (oldVersion < GROUP_STORY_REPLY_CLEANUP) {
      db.execSQL(
        """
        DELETE FROM mms
        WHERE 
          parent_story_id > 0 AND
          parent_story_id NOT IN (SELECT _id FROM mms WHERE remote_deleted = 0) 
        """.trimIndent()
      )
    }

    if (oldVersion < REMOTE_MEGAPHONE) {
      db.execSQL(
        """
          CREATE TABLE remote_megaphone (
            _id INTEGER PRIMARY KEY,
            uuid TEXT UNIQUE NOT NULL,
            priority INTEGER NOT NULL,
            countries TEXT,
            minimum_version INTEGER NOT NULL,
            dont_show_before INTEGER NOT NULL,
            dont_show_after INTEGER NOT NULL,
            show_for_days INTEGER NOT NULL,
            conditional_id TEXT,
            primary_action_id TEXT,
            secondary_action_id TEXT,
            image_url TEXT,
            image_uri TEXT DEFAULT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            primary_action_text TEXT,
            secondary_action_text TEXT,
            shown_at INTEGER DEFAULT 0,
            finished_at INTEGER DEFAULT 0
          )
        """
      )
    }

    if (oldVersion < QUOTE_INDEX) {
      db.execSQL(
        """
          CREATE INDEX IF NOT EXISTS mms_quote_id_quote_author_index ON mms (quote_id, quote_author)
        """
      )
    }

    if (oldVersion < MY_STORY_PRIVACY_MODE) {
      db.execSQL("ALTER TABLE distribution_list ADD COLUMN privacy_mode INTEGER DEFAULT 0")
      db.execSQL("UPDATE distribution_list SET privacy_mode = 1 WHERE _id = 1")

      db.execSQL(
        """
          CREATE TABLE distribution_list_member_tmp (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            list_id INTEGER NOT NULL REFERENCES distribution_list (_id) ON DELETE CASCADE,
            recipient_id INTEGER NOT NULL REFERENCES recipient (_id),
            privacy_mode INTEGER DEFAULT 0
          )
        """
      )

      db.execSQL(
        """
          INSERT INTO distribution_list_member_tmp
          SELECT
            _id,
            list_id,
            recipient_id,
            0
          FROM distribution_list_member
        """
      )

      db.execSQL("DROP TABLE distribution_list_member")
      db.execSQL("ALTER TABLE distribution_list_member_tmp RENAME TO distribution_list_member")

      db.execSQL("UPDATE distribution_list_member SET privacy_mode = 1 WHERE list_id = 1")

      db.execSQL("CREATE UNIQUE INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index ON distribution_list_member (list_id, recipient_id, privacy_mode)")
    }

    if (oldVersion < EXPIRING_PROFILE_CREDENTIALS) {
      db.execSQL("UPDATE recipient SET profile_key_credential = NULL")
    }
  }

  /**
   * Important: You can't change this method, or you risk breaking existing migrations. If you need to change this, make a new method.
   */
  private fun migrateReaction(db: SQLiteDatabase, cursor: Cursor, isMms: Boolean) {
    try {
      val messageId = CursorUtil.requireLong(cursor, "_id")
      val reactionList = ReactionList.ADAPTER.decode(CursorUtil.requireBlob(cursor, "reactions"))

      for (reaction in reactionList.reactions) {
        val contentValues = ContentValues().apply {
          put("message_id", messageId)
          put("is_mms", if (isMms) 1 else 0)
          put("author_id", reaction.author)
          put("emoji", reaction.emoji)
          put("date_sent", reaction.sentTime)
          put("date_received", reaction.receivedTime)
        }
        db.insert("reaction", null, contentValues)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse reaction!")
    }
  }

  /**
   * Important: You can't change this method, or you risk breaking existing migrations. If you need to change this, make a new method.
   */
  private fun getLocalAci(context: Application): ACI? {
    if (KeyValueDatabase.exists(context)) {
      val keyValueDatabase = KeyValueDatabase.getInstance(context).readableDatabase
      keyValueDatabase.query("key_value", arrayOf("value"), "key IN (?, ?)", SqlUtil.buildArgs("account.aci", "account.1.aci"), null, null, null).use { cursor ->
        return if (cursor.moveToFirst()) {
          ACI.parseOrNull(cursor.requireString("value"))
        } else {
          null
        }
      }
    } else {
      return ACI.parseOrNull(SecurePreferenceManager.getSecurePreferences(context).getString("pref_local_uuid", null))
    }
  }
}
