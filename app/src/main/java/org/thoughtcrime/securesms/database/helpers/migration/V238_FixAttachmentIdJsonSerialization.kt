package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.update

/**
 * Fix the JSON for AttachmentId in link_previews and shared_contacts message's columns.
 */
@Suppress("ClassName")
object V238_FixAttachmentIdJsonSerialization : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.rawQuery(
      """
          SELECT
            _id,
            link_previews
          FROM message
          WHERE link_previews NOT NULL
        """
    ).use { cursor ->
      while (cursor.moveToNext()) {
        var previewReplaced = false

        val id = cursor.requireLong("_id")
        val serializedPreviews = cursor.requireString("link_previews")

        val previews = JSONArray(serializedPreviews)
        for (i in 0 until previews.length()) {
          val preview = previews.optJSONObject(i)
          if (preview != null) {
            val attachmentId = preview.optJSONObject("attachmentId")
            if (attachmentId != null && attachmentId.has("id")) {
              val rowId = attachmentId.getLong("id")
              val newAttachmentId = JSONObject()
              newAttachmentId.put("rowId", rowId)
              preview.put("attachmentId", newAttachmentId)
              previewReplaced = true
            }
          }
        }

        if (previewReplaced) {
          db.update("message")
            .values("link_previews" to previews.toString())
            .where("_id = ?", id)
            .run()
        }
      }
    }
    db.rawQuery(
      """
          SELECT
            _id,
            shared_contacts
          FROM message
          WHERE shared_contacts NOT NULL
        """
    ).use { cursor ->
      while (cursor.moveToNext()) {
        var avatarReplaced = false

        val id = cursor.requireLong("_id")
        val serializedContacts = cursor.requireString("shared_contacts")

        val contacts = JSONArray(serializedContacts)
        for (i in 0 until contacts.length()) {
          val contact = contacts.optJSONObject(i)
          if (contact != null) {
            val avatar = contact.optJSONObject("avatar")
            if (avatar != null) {
              val attachmentId = avatar.optJSONObject("attachmentId")
              if (attachmentId != null && attachmentId.has("id")) {
                val rowId = attachmentId.getLong("id")
                val newAttachmentId = JSONObject()
                newAttachmentId.put("rowId", rowId)
                avatar.put("attachmentId", newAttachmentId)
                avatarReplaced = true
              }
            }
          }
        }

        if (avatarReplaced) {
          db.update("message")
            .values("shared_contacts" to contacts.toString())
            .where("_id = ?", id)
            .run()
        }
      }
    }
  }
}
