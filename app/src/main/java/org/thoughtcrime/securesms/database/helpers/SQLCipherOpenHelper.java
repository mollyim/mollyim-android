package org.thoughtcrime.securesms.database.helpers;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.thoughtcrime.securesms.contacts.avatars.ContactColorsLegacy;
import org.thoughtcrime.securesms.database.MentionDatabase;
import org.thoughtcrime.securesms.database.RemappedRecordsDatabase;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
import org.thoughtcrime.securesms.database.MegaphoneDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SignedPreKeyDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.StorageKeyDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.SecurePreferenceManager;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Triple;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SQLCipherOpenHelper extends SQLiteOpenHelper {

  @SuppressWarnings("unused")
  private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

  private static final int REACTIONS_UNREAD_INDEX           = 39;
  private static final int RESUMABLE_DOWNLOADS              = 40;
  private static final int KEY_VALUE_STORE                  = 41;
  private static final int ATTACHMENT_DISPLAY_ORDER         = 42;
  private static final int SPLIT_PROFILE_NAMES              = 43;
  private static final int STICKER_PACK_ORDER               = 44;
  private static final int MEGAPHONES                       = 45;
  private static final int MEGAPHONE_FIRST_APPEARANCE       = 46;
  private static final int PROFILE_KEY_TO_DB                = 47;
  private static final int PROFILE_KEY_CREDENTIALS          = 48;
  private static final int ATTACHMENT_FILE_INDEX            = 49;
  private static final int STORAGE_SERVICE_ACTIVE           = 50;
  private static final int GROUPS_V2_RECIPIENT_CAPABILITY   = 51;
  private static final int TRANSFER_FILE_CLEANUP            = 52;
  private static final int PROFILE_DATA_MIGRATION           = 53;
  private static final int AVATAR_LOCATION_MIGRATION        = 54;
  private static final int GROUPS_V2                        = 55;
  private static final int ATTACHMENT_UPLOAD_TIMESTAMP      = 56;
  private static final int ATTACHMENT_CDN_NUMBER            = 57;
  private static final int JOB_INPUT_DATA                   = 58;
  private static final int SERVER_TIMESTAMP                 = 59;
  private static final int REMOTE_DELETE                    = 60;
  private static final int COLOR_MIGRATION                  = 61;
  private static final int LAST_SCROLLED                    = 62;
  private static final int LAST_PROFILE_FETCH               = 63;
  private static final int SERVER_DELIVERED_TIMESTAMP       = 64;
  private static final int QUOTE_CLEANUP                    = 65;
  private static final int BORDERLESS                       = 66;
  private static final int REMAPPED_RECORDS                 = 67;
  private static final int MENTIONS                         = 68;
  private static final int PINNED_CONVERSATIONS             = 69;
  private static final int MENTION_GLOBAL_SETTING_MIGRATION = 70;
  private static final int UNKNOWN_STORAGE_FIELDS           = 71;
  private static final int STICKER_CONTENT_TYPE             = 72;
  private static final int STICKER_EMOJI_IN_NOTIFICATIONS   = 73;
  private static final int THUMBNAIL_CLEANUP                = 74;
  private static final int STICKER_CONTENT_TYPE_CLEANUP     = 75;
  private static final int MENTION_CLEANUP                  = 76;
  private static final int MENTION_CLEANUP_V2               = 77;
  private static final int REACTION_CLEANUP                 = 78;
  private static final int CAPABILITIES_REFACTOR            = 79;
  private static final int GV1_MIGRATION                    = 80;

  private static final int    DATABASE_VERSION = 80;
  private static final String DATABASE_NAME    = "signal.db";

  private final Context        context;
  private final DatabaseSecret databaseSecret;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION, new SQLiteDatabaseHook() {
      @Override
      public void preKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
        db.rawExecSQL("PRAGMA cipher_default_page_size = 4096;");
      }

      @Override
      public void postKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA kdf_iter = '1';");
        db.rawExecSQL("PRAGMA cipher_page_size = 4096;");
      }
    });

    this.context        = context.getApplicationContext();
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SmsDatabase.CREATE_TABLE);
    db.execSQL(MmsDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(IdentityDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientDatabase.CREATE_TABLE);
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE);
    db.execSQL(OneTimePreKeyDatabase.CREATE_TABLE);
    db.execSQL(SignedPreKeyDatabase.CREATE_TABLE);
    db.execSQL(SessionDatabase.CREATE_TABLE);
    db.execSQL(StickerDatabase.CREATE_TABLE);
    db.execSQL(StorageKeyDatabase.CREATE_TABLE);
    db.execSQL(KeyValueDatabase.CREATE_TABLE);
    db.execSQL(MegaphoneDatabase.CREATE_TABLE);
    db.execSQL(MentionDatabase.CREATE_TABLE);
    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, JobDatabase.CREATE_TABLE);
    executeStatements(db, RemappedRecordsDatabase.CREATE_TABLE);

    executeStatements(db, RecipientDatabase.CREATE_INDEXS);
    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, StickerDatabase.CREATE_INDEXES);
    executeStatements(db, StorageKeyDatabase.CREATE_INDEXES);
    executeStatements(db, MentionDatabase.CREATE_INDEXES);

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);
    long startTime = System.currentTimeMillis();

    db.beginTransaction();

    try {
      if (oldVersion < REACTIONS_UNREAD_INDEX) {
        throw new AssertionError("Unsupported Signal database: version is too old");
      }

      if (oldVersion < RESUMABLE_DOWNLOADS) {
        db.execSQL("ALTER TABLE part ADD COLUMN transfer_file TEXT DEFAULT NULL");
      }

      if (oldVersion < KEY_VALUE_STORE) {
        db.execSQL("CREATE TABLE key_value (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                           "key TEXT UNIQUE, " +
                                           "value TEXT, " +
                                           "type INTEGER)");
      }

      if (oldVersion < ATTACHMENT_DISPLAY_ORDER) {
        db.execSQL("ALTER TABLE part ADD COLUMN display_order INTEGER DEFAULT 0");
      }

      if (oldVersion < SPLIT_PROFILE_NAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_family_name TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_joined_name TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_PACK_ORDER) {
        db.execSQL("ALTER TABLE sticker ADD COLUMN pack_order INTEGER DEFAULT 0");
      }

      if (oldVersion < MEGAPHONES) {
        db.execSQL("CREATE TABLE megaphone (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                           "event TEXT UNIQUE, "  +
                                           "seen_count INTEGER, " +
                                           "last_seen INTEGER, "  +
                                           "finished INTEGER)");
      }

      if (oldVersion < MEGAPHONE_FIRST_APPEARANCE) {
        db.execSQL("ALTER TABLE megaphone ADD COLUMN first_visible INTEGER DEFAULT 0");
      }

      if (oldVersion < PROFILE_KEY_TO_DB) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (!TextUtils.isEmpty(localNumber)) {
          String        encodedProfileKey = SecurePreferenceManager.getSecurePreferences(context).getString("pref_profile_key", null);
          byte[]        profileKey        = encodedProfileKey != null ? Base64.decodeOrThrow(encodedProfileKey) : Util.getSecretBytes(32);
          ContentValues values            = new ContentValues(1);

          values.put("profile_key", Base64.encodeBytes(profileKey));

          if (db.update("recipient", values, "phone = ?", new String[]{localNumber}) == 0) {
            throw new AssertionError("No rows updated!");
          }
        }
      }

      if (oldVersion < PROFILE_KEY_CREDENTIALS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_key_credential TEXT DEFAULT NULL");
      }

      if (oldVersion < ATTACHMENT_FILE_INDEX) {
        db.execSQL("CREATE INDEX IF NOT EXISTS part_data_index ON part (_data)");
      }

      if (oldVersion < STORAGE_SERVICE_ACTIVE) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN group_type INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS recipient_group_type_index ON recipient (group_type)");

        db.execSQL("UPDATE recipient set group_type = 1 WHERE group_id NOT NULL AND group_id LIKE '__signal_mms_group__%'");
        db.execSQL("UPDATE recipient set group_type = 2 WHERE group_id NOT NULL AND group_id LIKE '__textsecure_group__%'");

        try (Cursor cursor = db.rawQuery("SELECT _id FROM recipient WHERE registered = 1 or group_type = 2", null)) {
          while (cursor != null && cursor.moveToNext()) {
            String        id     = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            ContentValues values = new ContentValues(1);

            values.put("dirty", 2);
            values.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            db.update("recipient", values, "_id = ?", new String[] { id });
          }
        }
      }

      if (oldVersion < GROUPS_V2_RECIPIENT_CAPABILITY) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN gv2_capability INTEGER DEFAULT 0");
      }

      if (oldVersion < TRANSFER_FILE_CLEANUP) {
        File partsDirectory = context.getDir("parts", Context.MODE_PRIVATE);

        if (partsDirectory.exists()) {
          File[] transferFiles = partsDirectory.listFiles((dir, name) -> name.startsWith("transfer"));
          int    deleteCount   = 0;

          Log.i(TAG, "Found " + transferFiles.length + " dangling transfer files.");

          for (File file : transferFiles) {
            if (file.delete()) {
              Log.i(TAG, "Deleted " + file.getName());
              deleteCount++;
            }
          }

          Log.i(TAG, "Deleted " + deleteCount + " dangling transfer files.");
        } else {
          Log.w(TAG, "Part directory did not exist. Skipping.");
        }
      }

      if (oldVersion < PROFILE_DATA_MIGRATION) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (localNumber != null) {
          String      encodedProfileName = SecurePreferenceManager.getSecurePreferences(context).getString("pref_profile_name", null);
          ProfileName profileName        = ProfileName.fromSerialized(encodedProfileName);

          db.execSQL("UPDATE recipient SET signal_profile_name = ?, profile_family_name = ?, profile_joined_name = ? WHERE phone = ?",
                     new String[] { profileName.getGivenName(), profileName.getFamilyName(), profileName.toString(), localNumber });
        }
      }

      if (oldVersion < AVATAR_LOCATION_MIGRATION) {
        File   oldAvatarDirectory = new File(context.getFilesDir(), "avatars");
        File[] results            = oldAvatarDirectory.listFiles();

        if (results != null) {
          Log.i(TAG, "Preparing to migrate " + results.length + " avatars.");

          for (File file : results) {
            if (Util.isLong(file.getName())) {
              try {
                AvatarHelper.setAvatar(context, RecipientId.from(file.getName()), new FileInputStream(file));
              } catch(IOException e) {
                Log.w(TAG, "Failed to copy file " + file.getName() + "! Skipping.");
              }
            } else {
              Log.w(TAG, "Invalid avatar name '" + file.getName() + "'! Skipping.");
            }
          }
        } else {
          Log.w(TAG, "No avatar directory files found.");
        }

        if (!FileUtils.deleteDirectory(oldAvatarDirectory)) {
          Log.w(TAG, "Failed to delete avatar directory.");
        }

        try (Cursor cursor = db.rawQuery("SELECT recipient_id, avatar FROM groups", null)) {
          while (cursor != null && cursor.moveToNext()) {
            RecipientId recipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow("recipient_id")));
            byte[]      avatar      = cursor.getBlob(cursor.getColumnIndexOrThrow("avatar"));

            try {
              AvatarHelper.setAvatar(context, recipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
            } catch (IOException e) {
              Log.w(TAG, "Failed to copy avatar for " + recipientId + "! Skipping.", e);
            }
          }
        }

        db.execSQL("UPDATE groups SET avatar_id = 0 WHERE avatar IS NULL");
        db.execSQL("UPDATE groups SET avatar = NULL");
      }

      if (oldVersion < GROUPS_V2) {
        db.execSQL("ALTER TABLE groups ADD COLUMN master_key");
        db.execSQL("ALTER TABLE groups ADD COLUMN revision");
        db.execSQL("ALTER TABLE groups ADD COLUMN decrypted_group");
      }

      if (oldVersion < ATTACHMENT_UPLOAD_TIMESTAMP) {
        db.execSQL("ALTER TABLE part ADD COLUMN upload_timestamp DEFAULT 0");
      }

      if (oldVersion < ATTACHMENT_CDN_NUMBER) {
        db.execSQL("ALTER TABLE part ADD COLUMN cdn_number INTEGER DEFAULT 0");
      }

      if (oldVersion < JOB_INPUT_DATA) {
        db.execSQL("ALTER TABLE job_spec ADD COLUMN serialized_input_data TEXT DEFAULT NULL");
      }

      if (oldVersion < SERVER_TIMESTAMP) {
        db.execSQL("ALTER TABLE sms ADD COLUMN date_server INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_date_server_index ON sms (date_server)");

        db.execSQL("ALTER TABLE mms ADD COLUMN date_server INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_date_server_index ON mms (date_server)");
      }

      if (oldVersion < REMOTE_DELETE) {
        db.execSQL("ALTER TABLE sms ADD COLUMN remote_deleted INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN remote_deleted INTEGER DEFAULT 0");
      }

      if (oldVersion < COLOR_MIGRATION) {
        try (Cursor cursor = db.rawQuery("SELECT _id, system_display_name FROM recipient WHERE system_display_name NOT NULL AND color IS NULL", null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   id   = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            String name = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"));

            ContentValues values = new ContentValues();
            values.put("color", ContactColorsLegacy.generateForV2(name).serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }
      }

      if (oldVersion < LAST_SCROLLED) {
        db.execSQL("ALTER TABLE thread ADD COLUMN last_scrolled INTEGER DEFAULT 0");
      }

      if (oldVersion < LAST_PROFILE_FETCH) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_profile_fetch INTEGER DEFAULT 0");
      }

      if (oldVersion < SERVER_DELIVERED_TIMESTAMP) {
        db.execSQL("ALTER TABLE push ADD COLUMN server_delivered_timestamp INTEGER DEFAULT 0");
      }

      if (oldVersion < QUOTE_CLEANUP) {
        String query = "SELECT _data " +
                       "FROM (SELECT _data, MIN(quote) AS all_quotes " +
                             "FROM part " +
                             "WHERE _data NOT NULL AND data_hash NOT NULL " +
                             "GROUP BY _data) " +
                       "WHERE all_quotes = 1";

        int count = 0;

        try (Cursor cursor = db.rawQuery(query, null)) {
          while (cursor != null && cursor.moveToNext()) {
            String data = cursor.getString(cursor.getColumnIndexOrThrow("_data"));

            if (new File(data).delete()) {
              ContentValues values = new ContentValues();
              values.putNull("_data");
              values.putNull("data_random");
              values.putNull("thumbnail");
              values.putNull("thumbnail_random");
              values.putNull("data_hash");
              db.update("part", values, "_data = ?", new String[] { data });

              count++;
            } else {
              Log.w(TAG, "[QuoteCleanup] Failed to delete " + data);
            }
          }
        }

        Log.i(TAG, "[QuoteCleanup] Cleaned up " + count + " quotes.");
      }

      if (oldVersion < BORDERLESS) {
        db.execSQL("ALTER TABLE part ADD COLUMN borderless INTEGER DEFAULT 0");
      }

      if (oldVersion < REMAPPED_RECORDS) {
        db.execSQL("CREATE TABLE remapped_recipients (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                     "old_id INTEGER UNIQUE, " +
                                                     "new_id INTEGER)");
        db.execSQL("CREATE TABLE remapped_threads (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                  "old_id INTEGER UNIQUE, " +
                                                  "new_id INTEGER)");
      }

      if (oldVersion < MENTIONS) {
        db.execSQL("CREATE TABLE mention (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "thread_id INTEGER, " +
                                         "message_id INTEGER, " +
                                         "recipient_id INTEGER, " +
                                         "range_start INTEGER, " +
                                         "range_length INTEGER)");

        db.execSQL("CREATE INDEX IF NOT EXISTS mention_message_id_index ON mention (message_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id);");

        db.execSQL("ALTER TABLE mms ADD COLUMN quote_mentions BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN mentions_self INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE recipient ADD COLUMN mention_setting INTEGER DEFAULT 0");
      }

      if (oldVersion < PINNED_CONVERSATIONS) {
        db.execSQL("ALTER TABLE thread ADD COLUMN pinned INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS thread_pinned_index ON thread (pinned)");
      }

      if (oldVersion < MENTION_GLOBAL_SETTING_MIGRATION) {
        ContentValues updateAlways = new ContentValues();
        updateAlways.put("mention_setting", 0);
        db.update("recipient", updateAlways, "mention_setting = 1", null);

        ContentValues updateNever = new ContentValues();
        updateNever.put("mention_setting", 1);
        db.update("recipient", updateNever, "mention_setting = 2", null);
      }

      if (oldVersion < UNKNOWN_STORAGE_FIELDS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN storage_proto TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_CONTENT_TYPE) {
        db.execSQL("ALTER TABLE sticker ADD COLUMN content_type TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_EMOJI_IN_NOTIFICATIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_emoji TEXT DEFAULT NULL");
      }

      if (oldVersion < THUMBNAIL_CLEANUP) {
        int total   = 0;
        int deleted = 0;

        try (Cursor cursor = db.rawQuery("SELECT thumbnail FROM part WHERE thumbnail NOT NULL", null)) {
          if (cursor != null) {
            total = cursor.getCount();
            Log.w(TAG, "Found " + total + " thumbnails to delete.");
          }

          while (cursor != null && cursor.moveToNext()) {
            File file = new File(CursorUtil.requireString(cursor, "thumbnail"));

            if (file.delete()) {
              deleted++;
            } else {
              Log.w(TAG, "Failed to delete file! " + file.getAbsolutePath());
            }
          }
        }

        Log.w(TAG, "Deleted " + deleted + "/" + total + " thumbnail files.");
      }

      if (oldVersion < STICKER_CONTENT_TYPE_CLEANUP) {
        ContentValues values = new ContentValues();
        values.put("ct", "image/webp");

        String query = "sticker_id NOT NULL AND (ct IS NULL OR ct = '')";

        int rows = db.update("part", values, query, null);
        Log.i(TAG, "Updated " + rows + " sticker attachment content types.");
      }

      if (oldVersion < MENTION_CLEANUP) {
        String selectMentionIdsNotInGroupsV2 = "select mention._id from mention left join thread on mention.thread_id = thread._id left join recipient on thread.recipient_ids = recipient._id where recipient.group_type != 3";
        db.delete("mention", "_id in (" + selectMentionIdsNotInGroupsV2 + ")", null);
        db.delete("mention", "message_id NOT IN (SELECT _id FROM mms) OR thread_id NOT IN (SELECT _id from thread)", null);

        List<Long> idsToDelete = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id", null)) {
          while (cursor != null && cursor.moveToNext()) {
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body == null || body.isEmpty() || rangeStart < 0 || rangeLength < 0 || (rangeStart + rangeLength) > body.length()) {
              idsToDelete.add(CursorUtil.requireLong(cursor, "_id"));
            }
          }
        }

        if (Util.hasItems(idsToDelete)) {
          String ids = TextUtils.join(",", idsToDelete);
          db.delete("mention", "_id in (" + ids + ")", null);
        }
      }

      if (oldVersion < MENTION_CLEANUP_V2) {
        String selectMentionIdsWithMismatchingThreadIds = "select mention._id from mention left join mms on mention.message_id = mms._id where mention.thread_id != mms.thread_id";
        db.delete("mention", "_id in (" + selectMentionIdsWithMismatchingThreadIds + ")", null);

        List<Long>                          idsToDelete   = new LinkedList<>();
        Set<Triple<Long, Integer, Integer>> mentionTuples = new HashSet<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id order by mention._id desc", null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   mentionId   = CursorUtil.requireLong(cursor, "_id");
            long   messageId   = CursorUtil.requireLong(cursor, "message_id");
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body != null && rangeStart < body.length() && body.charAt(rangeStart) != '\uFFFC') {
              idsToDelete.add(mentionId);
            } else {
              Triple<Long, Integer, Integer> tuple = new Triple<>(messageId, rangeStart, rangeLength);
              if (mentionTuples.contains(tuple)) {
                idsToDelete.add(mentionId);
              } else {
                mentionTuples.add(tuple);
              }
            }
          }

          if (Util.hasItems(idsToDelete)) {
            String ids = TextUtils.join(",", idsToDelete);
            db.delete("mention", "_id in (" + ids + ")", null);
          }
        }
      }

      if (oldVersion < REACTION_CLEANUP) {
        ContentValues values = new ContentValues();
        values.putNull("reactions");
        db.update("sms", values, "remote_deleted = ?", new String[] { "1" });
      }

      if (oldVersion < CAPABILITIES_REFACTOR) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN capabilities INTEGER DEFAULT 0");

        db.execSQL("UPDATE recipient SET capabilities = 1 WHERE gv2_capability = 1");
        db.execSQL("UPDATE recipient SET capabilities = 2 WHERE gv2_capability = -1");
      }

      if (oldVersion < GV1_MIGRATION) {
        db.execSQL("ALTER TABLE groups ADD COLUMN expected_v2_id TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE groups ADD COLUMN former_v1_members TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON groups (expected_v2_id)");

        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT * FROM groups WHERE group_id LIKE '__textsecure_group__!%' AND LENGTH(group_id) = 53", null)) {
          while (cursor.moveToNext()) {
            String gv1 = CursorUtil.requireString(cursor, "group_id");
            String gv2 = GroupId.parseOrThrow(gv1).requireV1().deriveV2MigrationGroupId().toString();

            ContentValues values = new ContentValues();
            values.put("expected_v2_id", gv2);
            count += db.update("groups", values, "group_id = ?", SqlUtil.buildArgs(gv1));
          }
        }

        Log.i(TAG, "Updated " + count + " GV1 groups with expected GV2 IDs.");
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
  }

  public SQLiteDatabase getReadableDatabase() {
    return getReadableDatabase(databaseSecret.asString());
  }

  public SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  public static boolean databaseFileExists(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME).exists();
  }

  public static File getDatabaseFile(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME);
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
