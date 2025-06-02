package org.thoughtcrime.securesms.notes

import org.thoughtcrime.securesms.database.NoteDao
import org.thoughtcrime.securesms.database.SignalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// NoteDao is now passed in for testability.
class NotesRepository(private val noteDao: NoteDao) {

    suspend fun createNote(title: String, content: String, colorId: Long? = null): Long {
        return withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            val newNoteEntity = NoteEntity(
                id = 0, // ID is 0 to allow SQLite to autogenerate it
                title = title,
                content = content,
                colorId = colorId,
                createdAt = currentTime,
                updatedAt = currentTime
            )
            noteDao.insert(newNoteEntity)
        }
    }

    suspend fun updateNote(noteEntity: NoteEntity) {
        withContext(Dispatchers.IO) {
            val updatedNoteEntity = noteEntity.copy(updatedAt = System.currentTimeMillis())
            noteDao.update(updatedNoteEntity)
        }
    }

    suspend fun deleteNote(noteId: Long) {
        withContext(Dispatchers.IO) {
            noteDao.delete(noteId)
        }
    }

    suspend fun deleteNotes(noteIds: List<Long>) {
        if (noteIds.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            SignalDatabase.runInTransaction {
                for (noteId in noteIds) {
                    noteDao.delete(noteId)
                }
            }
        }
    }

    // Consider making these suspend functions with withContext(Dispatchers.IO) if
    // they are expected to be called from the main thread frequently.
    // For now, matching DAO's direct return style.
    fun getNoteById(noteId: Long): NoteEntity? {
        return noteDao.getNoteById(noteId)
    }

    fun getAllNotesSortedByTitle(): List<NoteEntity> {
        return noteDao.getAllNotesSortedByTitle()
    }

    fun getAllNotesFilteredByColor(colorId: Long): List<NoteEntity> {
        return noteDao.getAllNotesFilteredByColor(colorId)
    }

    // Optional MVP+ methods for NoteColorEntity - will add if NoteDao gets corresponding methods
    // For now, these are placeholders or would be added in a future subtask.
    // fun getAllNoteColors(): List<NoteColorEntity>
    // fun getNoteColorById(colorId: Long): NoteColorEntity?
}
