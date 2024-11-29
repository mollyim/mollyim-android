package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.IOException

object V190_UpdatePendingSelfDataMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V190_UpdatePendingSelfDataMigration::class.java)

  private const val PLACEHOLDER_ID = -2L

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val selfId: RecipientId = getSelfId(db) ?: return

    db.execSQL(
      """
        UPDATE message
        SET
          from_recipient_id = ${selfId.toLong()}
        WHERE from_recipient_id =  $PLACEHOLDER_ID
        """
    )

    db.execSQL(
      """
        UPDATE message
        SET
          to_recipient_id = ${selfId.toLong()}
        WHERE to_recipient_id =  $PLACEHOLDER_ID
        """
    )

    db.execSQL(
      """
        DELETE FROM recipient
        WHERE _id =  $PLACEHOLDER_ID
        """
    )

    db.rawQuery(
      """
        SELECT
          _id,
          snippet_extras
        FROM thread
        WHERE snippet_extras NOT NULL
      """
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
        val extraString = cursor.getString(cursor.getColumnIndexOrThrow("snippet_extras"))
        if (extraString != null) {
          val extra = try {
            JsonUtils.fromJson(extraString, ThreadTable.Extra::class.java)
          } catch (e: IOException) {
            Log.w(TAG, "Failed to decode extras!")
            null
          }
          if (extra?.individualRecipientId?.toLong() == PLACEHOLDER_ID) {
            val newExtraString = JsonUtils.toJson(extra.copy(individualRecipientId = "${selfId.toLong()}"))
            db.update("thread")
              .values("snippet_extras" to newExtraString)
              .where("_id = ?", id)
              .run()
          }
        }
      }
    }
  }

  private fun getSelfId(db: SQLiteDatabase): RecipientId? {
    val idByAci: RecipientId? = getLocalAci(AppDependencies.application)?.let { aci ->
      db.rawQuery("SELECT _id FROM recipient WHERE uuid = ?", SqlUtil.buildArgs(aci))
        .readToSingleObject { RecipientId.from(it.requireLong("_id")) }
    }

    if (idByAci != null) {
      return idByAci
    }

    Log.w(TAG, "Failed to find by ACI! Will try by E164.")

    val idByE164: RecipientId? = getLocalE164(AppDependencies.application)?.let { e164 ->
      db.rawQuery("SELECT _id FROM recipient WHERE phone = ?", SqlUtil.buildArgs(e164))
        .readToSingleObject { RecipientId.from(it.requireLong("_id")) }
    }

    if (idByE164 == null) {
      Log.w(TAG, "Also failed to find by E164!")
    }

    return idByE164
  }

  private fun getLocalAci(context: Application): ServiceId.ACI? {
    if (KeyValueDatabase.exists(context)) {
      val keyValueDatabase = KeyValueDatabase.getInstance(context).readableDatabase
      keyValueDatabase.query("key_value", arrayOf("value"), "key IN (?, ?)", SqlUtil.buildArgs("account.aci", "account.1.aci"), null, null, null).use { cursor ->
        return if (cursor.moveToFirst()) {
          ServiceId.ACI.parseOrNull(cursor.requireString("value"))
        } else {
          Log.w(TAG, "ACI not present in KV database!")
          null
        }
      }
    } else {
      Log.w(TAG, "Pre-KV database -- searching for ACI in shared prefs.")
      return ServiceId.ACI.parseOrNull(SecurePreferenceManager.getSecurePreferences(context).getString("pref_local_uuid", null))
    }
  }

  private fun getLocalE164(context: Application): String? {
    if (KeyValueDatabase.exists(context)) {
      val keyValueDatabase = KeyValueDatabase.getInstance(context).readableDatabase
      keyValueDatabase.query("key_value", arrayOf("value"), "key IN (?, ?)", SqlUtil.buildArgs("account.e164", "account.1.e164"), null, null, null).use { cursor ->
        return if (cursor.moveToFirst()) {
          cursor.requireString("value")
        } else {
          Log.w(TAG, "E164 not present in KV database!")
          null
        }
      }
    } else {
      Log.w(TAG, "Pre-KV database -- searching for E164 in shared prefs.")
      return SecurePreferenceManager.getSecurePreferences(context).getString("pref_local_number", null)
    }
  }
}
