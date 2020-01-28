package org.thoughtcrime.securesms.database.helpers;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
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
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;
import java.util.List;

public class SQLCipherOpenHelper extends SQLiteOpenHelper {

  @SuppressWarnings("unused")
  private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

  private static final int RESUMABLE_DOWNLOADS              = 40;
  private static final int KEY_VALUE_STORE                  = 41;
  private static final int ATTACHMENT_DISPLAY_ORDER         = 42;
  private static final int SPLIT_PROFILE_NAMES              = 43;
  private static final int STICKER_PACK_ORDER               = 44;

  private static final int    DATABASE_VERSION = 44;
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
    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, JobDatabase.CREATE_TABLE);

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
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);
    long startTime = System.currentTimeMillis();

    db.beginTransaction();

    try {

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

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
