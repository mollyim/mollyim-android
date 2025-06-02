package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.readableDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.writableDatabase
import org.thoughtcrime.securesms.notes.NoteColorEntity
import org.thoughtcrime.securesms.notes.NoteEntity

class NoteDao {

    companion object {
        const val NOTES_TABLE = "notes"
        const val NOTE_COLORS_TABLE = "note_colors" // Added for clarity, though not directly used in this DAO's public methods yet

        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_COLOR_ID = "color_id"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"

        // NoteColorTable columns (for mapCursorToNoteColorEntity)
        const val COLUMN_NAME = "name"
        const val COLUMN_HEX_VALUE = "hex_value"
    }

    fun insert(noteEntity: NoteEntity): Long {
        val values = ContentValues().apply {
            // For AUTOINCREMENT, if id is 0 or null, it will be generated.
            // If a specific id is provided, SQLite will try to use it.
            if (noteEntity.id > 0) { // Assuming 0 or less means "generate new"
                put(COLUMN_ID, noteEntity.id)
            }
            put(COLUMN_TITLE, noteEntity.title)
            put(COLUMN_CONTENT, noteEntity.content)
            noteEntity.colorId?.let { put(COLUMN_COLOR_ID, it) }
            put(COLUMN_CREATED_AT, noteEntity.createdAt)
            put(COLUMN_UPDATED_AT, noteEntity.updatedAt)
        }
        return writableDatabase.insert(NOTES_TABLE, null, values)
    }

    fun update(noteEntity: NoteEntity) {
        val values = ContentValues().apply {
            put(COLUMN_TITLE, noteEntity.title)
            put(COLUMN_CONTENT, noteEntity.content)
            noteEntity.colorId?.let { put(COLUMN_COLOR_ID, it) }
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        writableDatabase.update(NOTES_TABLE, values, "$COLUMN_ID = ?", arrayOf(noteEntity.id.toString()))
    }

    fun delete(noteId: Long) {
        writableDatabase.delete(NOTES_TABLE, "$COLUMN_ID = ?", arrayOf(noteId.toString()))
    }

    @SuppressLint("Range")
    fun getNoteById(noteId: Long): NoteEntity? {
        readableDatabase.query("SELECT * FROM $NOTES_TABLE WHERE $COLUMN_ID = ? LIMIT 1", arrayOf(noteId.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                return mapCursorToNoteEntity(cursor)
            }
        }
        return null
    }

    @SuppressLint("Range")
    fun getAllNotesSortedByTitle(): List<NoteEntity> {
        val notes = mutableListOf<NoteEntity>()
        readableDatabase.query("SELECT * FROM $NOTES_TABLE ORDER BY $COLUMN_TITLE ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(mapCursorToNoteEntity(cursor))
            }
        }
        return notes
    }

    @SuppressLint("Range")
    fun getAllNotesFilteredByColor(colorId: Long): List<NoteEntity> {
        val notes = mutableListOf<NoteEntity>()
        readableDatabase.query("SELECT * FROM $NOTES_TABLE WHERE $COLUMN_COLOR_ID = ? ORDER BY $COLUMN_UPDATED_AT DESC", arrayOf(colorId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(mapCursorToNoteEntity(cursor))
            }
        }
        return notes
    }

    @SuppressLint("Range")
    private fun mapCursorToNoteEntity(cursor: Cursor): NoteEntity {
        return NoteEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
            colorId = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_COLOR_ID))) null else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COLOR_ID)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
        )
    }

    @SuppressLint("Range")
    private fun mapCursorToNoteColorEntity(cursor: Cursor): NoteColorEntity {
        return NoteColorEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
            hexValue = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HEX_VALUE))
        )
    }
}
