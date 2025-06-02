package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.thoughtcrime.securesms.notes.NoteEntity

class NoteDaoTest {

    private lateinit var mockDb: SupportSQLiteDatabase
    private lateinit var mockSignalDatabase: MockedStatic<SignalDatabase>
    private lateinit var noteDao: NoteDao

    @BeforeEach
    fun setUp() {
        mockDb = mock()
        mockSignalDatabase = Mockito.mockStatic(SignalDatabase::class.java)
        mockSignalDatabase.`when`<SupportSQLiteDatabase> { SignalDatabase.readableDatabase }.thenReturn(mockDb)
        mockSignalDatabase.`when`<SupportSQLiteDatabase> { SignalDatabase.writableDatabase }.thenReturn(mockDb)

        noteDao = NoteDao()
    }

    @AfterEach
    fun tearDown() {
        mockSignalDatabase.close()
    }

    @Nested
    inner class InsertNote {
        @Test
        fun `insert should pass correct ContentValues to db_insert and return rowId`() {
            val note = NoteEntity(0, "Title", "Content", 1L, 1000L, 1000L)
            val expectedRowId = 1L
            whenever(mockDb.insert(eq(NoteDao.NOTES_TABLE), any(), any())).thenReturn(expectedRowId)

            val rowId = noteDao.insert(note)

            assertEquals(expectedRowId, rowId)
            verify(mockDb).insert(eq(NoteDao.NOTES_TABLE), eq(null), argThat { cv ->
                cv.getAsString(NoteDao.COLUMN_TITLE) == note.title &&
                cv.getAsString(NoteDao.COLUMN_CONTENT) == note.content &&
                cv.getAsLong(NoteDao.COLUMN_COLOR_ID) == note.colorId &&
                cv.getAsLong(NoteDao.COLUMN_CREATED_AT) == note.createdAt &&
                cv.getAsLong(NoteDao.COLUMN_UPDATED_AT) == note.updatedAt &&
                !cv.containsKey(NoteDao.COLUMN_ID) // ID should not be in CV for auto-increment if id is 0
            })
        }

        @Test
        fun `insert with specific ID should pass ID in ContentValues`() {
            val note = NoteEntity(5L, "Title", "Content", 1L, 1000L, 1000L)
            val expectedRowId = 5L
            whenever(mockDb.insert(eq(NoteDao.NOTES_TABLE), any(), any())).thenReturn(expectedRowId)

            val rowId = noteDao.insert(note)

            assertEquals(expectedRowId, rowId)
            verify(mockDb).insert(eq(NoteDao.NOTES_TABLE), eq(null), argThat { cv ->
                cv.getAsLong(NoteDao.COLUMN_ID) == note.id && // ID should be in CV
                cv.getAsString(NoteDao.COLUMN_TITLE) == note.title
            })
        }
    }

    @Nested
    inner class UpdateNote {
        @Test
        fun `update should pass correct ContentValues and WHERE clause to db_update`() {
            val note = NoteEntity(1L, "New Title", "New Content", 2L, 1000L, 2000L)
            // Note: The DAO's update method currently sets its own System.currentTimeMillis() for updated_at

            noteDao.update(note)

            verify(mockDb).update(
                eq(NoteDao.NOTES_TABLE),
                argThat { cv: ContentValues ->
                    cv.getAsString(NoteDao.COLUMN_TITLE) == note.title &&
                    cv.getAsString(NoteDao.COLUMN_CONTENT) == note.content &&
                    cv.getAsLong(NoteDao.COLUMN_COLOR_ID) == note.colorId &&
                    cv.containsKey(NoteDao.COLUMN_UPDATED_AT) // Check that updated_at is being set
                },
                eq("${NoteDao.COLUMN_ID} = ?"),
                argThat { args: Array<String> -> args[0] == note.id.toString() }
            )
        }
    }

    @Nested
    inner class DeleteNote {
        @Test
        fun `delete should use correct WHERE clause`() {
            val noteId = 1L
            noteDao.delete(noteId)
            verify(mockDb).delete(
                eq(NoteDao.NOTES_TABLE),
                eq("${NoteDao.COLUMN_ID} = ?"),
                argThat { args: Array<String> -> args[0] == noteId.toString() }
            )
        }
    }

    private fun mockCursorForNote(note: NoteEntity): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getLong(cursor.getColumnIndexOrThrow(NoteDao.COLUMN_ID))).thenReturn(note.id)
        whenever(cursor.getString(cursor.getColumnIndexOrThrow(NoteDao.COLUMN_TITLE))).thenReturn(note.title)
        whenever(cursor.getString(cursor.getColumnIndexOrThrow(NoteDao.COLUMN_CONTENT))).thenReturn(note.content)
        val colorIdColumnIndex = cursor.getColumnIndexOrThrow(NoteDao.COLUMN_COLOR_ID)
        if (note.colorId == null) {
            whenever(cursor.isNull(colorIdColumnIndex)).thenReturn(true)
            whenever(cursor.getLong(colorIdColumnIndex)).thenReturn(0L) // or some default for getLong when null
        } else {
            whenever(cursor.isNull(colorIdColumnIndex)).thenReturn(false)
            whenever(cursor.getLong(colorIdColumnIndex)).thenReturn(note.colorId)
        }
        whenever(cursor.getLong(cursor.getColumnIndexOrThrow(NoteDao.COLUMN_CREATED_AT))).thenReturn(note.createdAt)
        whenever(cursor.getLong(cursor.getColumnIndexOrThrow(NoteDao.COLUMN_UPDATED_AT))).thenReturn(note.updatedAt)
        return cursor
    }

    @Nested
    inner class GetNoteById {
        @Test
        fun `getNoteById should parse cursor correctly`() {
            val noteId = 1L
            val expectedNote = NoteEntity(noteId, "Title", "Content", 1L, 1000L, 2000L)
            val mockCursor = mockCursorForNote(expectedNote)
            whenever(mockDb.query(any<String>(), any<Array<String>>())).thenReturn(mockCursor)

            val result = noteDao.getNoteById(noteId)

            assertNotNull(result)
            assertEquals(expectedNote, result)
            verify(mockDb).query(
                argThat { sql: String -> sql.contains("SELECT * FROM ${NoteDao.NOTES_TABLE}") && sql.contains("${NoteDao.COLUMN_ID} = ?") && sql.contains("LIMIT 1") },
                argThat { args: Array<String> -> args[0] == noteId.toString() }
            )
        }

        @Test
        fun `getNoteById should return null if cursor is empty`() {
            val noteId = 1L
            val mockCursor = mock<Cursor>()
            whenever(mockCursor.moveToFirst()).thenReturn(false)
            whenever(mockDb.query(any<String>(), any<Array<String>>())).thenReturn(mockCursor)

            val result = noteDao.getNoteById(noteId)
            assertNull(result)
        }
    }

    @Nested
    inner class GetAllNotesSortedByTitle {
        @Test
        fun `getAllNotesSortedByTitle should query correctly and parse multiple items`() {
            val note1 = NoteEntity(1L, "Apple", "ContentA", 1L, 1000L, 2000L)
            val note2 = NoteEntity(2L, "Banana", "ContentB", null, 1100L, 2100L)

            val mockCursor = mock<Cursor>()
            whenever(mockCursor.moveToNext()).thenReturn(true, true, false) // Two items

            // Setup individual getColumnIndexOrThrow calls if not using the helper extensively for this specific test
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_ID)).thenReturn(0)
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_TITLE)).thenReturn(1)
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_CONTENT)).thenReturn(2)
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_COLOR_ID)).thenReturn(3)
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_CREATED_AT)).thenReturn(4)
            whenever(mockCursor.getColumnIndexOrThrow(NoteDao.COLUMN_UPDATED_AT)).thenReturn(5)

            // First item
            whenever(mockCursor.getLong(0)).thenReturn(note1.id, note2.id) // Called twice
            whenever(mockCursor.getString(1)).thenReturn(note1.title, note2.title)
            whenever(mockCursor.getString(2)).thenReturn(note1.content, note2.content)
            whenever(mockCursor.isNull(3)).thenReturn(false, true) // note1.colorId not null, note2.colorId is null
            whenever(mockCursor.getLong(3)).thenReturn(note1.colorId!!, 0L) // 0L for the null colorId case
            whenever(mockCursor.getLong(4)).thenReturn(note1.createdAt, note2.createdAt)
            whenever(mockCursor.getLong(5)).thenReturn(note1.updatedAt, note2.updatedAt)

            whenever(mockDb.query(argThat<String> { sql -> sql.contains("ORDER BY ${NoteDao.COLUMN_TITLE} ASC") }, eq(null))).thenReturn(mockCursor)

            val results = noteDao.getAllNotesSortedByTitle()

            assertEquals(2, results.size)
            assertEquals(note1, results[0])
            assertEquals(note2, results[1])

            verify(mockDb).query(
                argThat { sql: String -> sql.contains("SELECT * FROM ${NoteDao.NOTES_TABLE}") && sql.contains("ORDER BY ${NoteDao.COLUMN_TITLE} ASC") },
                eq(null)
            )
        }
    }

    @Nested
    inner class GetAllNotesFilteredByColor {
        @Test
        fun `getAllNotesFilteredByColor should query with colorId and sorted by updated_at DESC`() {
            val colorIdFilter = 3L
            val note = NoteEntity(1L, "Title", "Content", colorIdFilter, 1000L, 2000L)
            val mockCursor = mockCursorForNote(note) // Re-use helper, it sets up one item
            whenever(mockDb.query(any<String>(), any<Array<String>>())).thenReturn(mockCursor)

            val results = noteDao.getAllNotesFilteredByColor(colorIdFilter)

            assertNotNull(results)
            assertEquals(1, results.size)
            assertEquals(note, results[0])

            verify(mockDb).query(
                argThat { sql: String ->
                    sql.contains("SELECT * FROM ${NoteDao.NOTES_TABLE}") &&
                    sql.contains("${NoteDao.COLUMN_COLOR_ID} = ?") &&
                    sql.contains("ORDER BY ${NoteDao.COLUMN_UPDATED_AT} DESC")
                },
                argThat { args: Array<String> -> args[0] == colorIdFilter.toString() }
            )
        }
    }
}
