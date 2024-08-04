/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.update

/**
 * Add columns to group and group membership tables needed for group send endorsements.
 */
@Suppress("ClassName")
object V238_AddGroupSendEndorsementsColumns : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE groups ADD COLUMN group_send_endorsements_expiration INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE group_membership ADD COLUMN endorsement BLOB DEFAULT NULL")

    // MOLLY: Fix the JSON for AttachmentId in link_previews and shared_contacts message's columns
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
          val preview = previews.getJSONObject(i)
          if (preview.has("attachmentId")) {
            val attachmentId = preview.getJSONObject("attachmentId")
            if (attachmentId.has("id")) {
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
          val contact = contacts.getJSONObject(i)
          if (contact.has("avatar")) {
            val avatar = contact.getJSONObject("avatar")
            if (avatar.has("attachmentId")) {
              val attachmentId = avatar.getJSONObject("attachmentId")
              if (attachmentId.has("id")) {
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
