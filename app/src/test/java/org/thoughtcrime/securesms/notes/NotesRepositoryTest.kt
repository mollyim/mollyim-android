package org.thoughtcrime.securesms.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.thoughtcrime.securesms.database.NoteDao
import org.thoughtcrime.securesms.database.SignalDatabase // For runInTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.MockedStatic
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class NotesRepositoryTest {

    private lateinit var noteDao: NoteDao
    private lateinit var repository: NotesRepository
    private lateinit var mockSignalDatabase: MockedStatic<SignalDatabase>

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        noteDao = mock()
        repository = NotesRepository(noteDao) // Assuming constructor injection for NoteDao

        // Mock SignalDatabase.runInTransaction
        // Needed because NotesRepository directly calls SignalDatabase.runInTransaction
        mockSignalDatabase = Mockito.mockStatic(SignalDatabase::class.java)
        mockSignalDatabase.`when`<Any> { SignalDatabase.runInTransaction<Any>(any()) }.thenAnswer { invocation ->
            val block = invocation.getArgument<(() -> Any)>(0)
            block() // Execute the block directly for testing purposes
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        mockSignalDatabase.close()
    }

    @Nested
    inner class CreateNote {
        @Test
        fun `createNote should call dao_insert with correct NoteEntity and return new id`() = runTest {
            val title = "Test Title"
            val content = "Test Content"
            val colorId = 1L
            val expectedNewId = 123L

            whenever(noteDao.insert(any())).thenReturn(expectedNewId)

            val resultId = repository.createNote(title, content, colorId)

            assertEquals(expectedNewId, resultId)
            verify(noteDao).insert(argThat { entity ->
                entity.id == 0L &&
                entity.title == title &&
                entity.content == content &&
                entity.colorId == colorId &&
                entity.createdAt > 0 &&
                entity.updatedAt == entity.createdAt
            })
        }
    }

    @Nested
    inner class UpdateNote {
        @Test
        fun `updateNote should call dao_update with updated timestamp`() = runTest {
            val originalEntity = NoteEntity(1L, "Old Title", "Old Content", null, 1000L, 1000L)
            val initialTimestamp = originalEntity.updatedAt

            // Ensure time progresses for updatedAt
            // This is tricky to test precisely without injecting a clock.
            // We will check that updatedAt is greater than the original.
            // For more robust testing, a Clock interface would be injected.

            repository.updateNote(originalEntity)

            verify(noteDao).update(argThat { entity ->
                entity.id == originalEntity.id &&
                entity.title == originalEntity.title && // Title should not change unless explicitly set
                entity.updatedAt > initialTimestamp
            })
        }
    }

    @Nested
    inner class DeleteNote {
        @Test
        fun `deleteNote should call dao_delete with correct id`() = runTest {
            val noteId = 1L
            repository.deleteNote(noteId)
            verify(noteDao).delete(noteId)
        }
    }

    @Nested
    inner class DeleteNotes {
        @Test
        fun `deleteNotes should call dao_delete for each id within a transaction`() = runTest {
            val noteIds = listOf(1L, 2L, 3L)
            repository.deleteNotes(noteIds)

            // Verify runInTransaction was called (implicitly tested by mock setup executing the block)
            // Verify delete was called for each ID
            verify(noteDao, times(noteIds.size)).delete(any())
            noteIds.forEach { id ->
                verify(noteDao).delete(id)
            }
            // Verify that SignalDatabase.runInTransaction was indeed called
            mockSignalDatabase.verify { SignalDatabase.runInTransaction<Any>(any()) }
        }

        @Test
        fun `deleteNotes with empty list should not call dao_delete or runInTransaction`() = runTest {
            repository.deleteNotes(emptyList())
            verify(noteDao, never()).delete(any())
            mockSignalDatabase.verify(never()) { SignalDatabase.runInTransaction<Any>(any()) }
        }
    }

    @Nested
    inner class GetNoteById {
        @Test
        fun `getNoteById should call dao_getNoteById and return its result`() {
            val noteId = 1L
            val expectedNote = NoteEntity(noteId, "Title", "Content", null, 0L, 0L)
            whenever(noteDao.getNoteById(noteId)).thenReturn(expectedNote)

            val result = repository.getNoteById(noteId)

            assertEquals(expectedNote, result)
            verify(noteDao).getNoteById(noteId)
        }
    }

    @Nested
    inner class GetAllNotesSortedByTitle {
        @Test
        fun `getAllNotesSortedByTitle should call dao_getAllNotesSortedByTitle and return its result`() {
            val expectedNotes = listOf(NoteEntity(1L, "Apple", "", null, 0L, 0L))
            whenever(noteDao.getAllNotesSortedByTitle()).thenReturn(expectedNotes)

            val result = repository.getAllNotesSortedByTitle()

            assertEquals(expectedNotes, result)
            verify(noteDao).getAllNotesSortedByTitle()
        }
    }

    @Nested
    inner class GetAllNotesFilteredByColor {
         @Test
        fun `getAllNotesFilteredByColor should call dao_getAllNotesFilteredByColor and return its result`() {
            val colorId = 1L
            val expectedNotes = listOf(NoteEntity(1L, "Note", "", colorId, 0L, 0L))
            whenever(noteDao.getAllNotesFilteredByColor(colorId)).thenReturn(expectedNotes)

            val result = repository.getAllNotesFilteredByColor(colorId)

            assertEquals(expectedNotes, result)
            verify(noteDao).getAllNotesFilteredByColor(colorId)
        }
    }
}

// Minimalist constructor for NotesRepository for testing, assuming NoteDao is passed in.
// If NoteDao is instantiated internally, this test setup would need to change.
// Based on previous subtask, NotesRepository instantiates NoteDao internally.
// So, the actual NotesRepository will be used, and its internal NoteDao mock will be tricky.
// The provided solution for NotesRepository was:
// class NotesRepository { private val noteDao: NoteDao = NoteDao() ... }
// This means we cannot directly inject a mock NoteDao unless we modify NotesRepository
// or use a more complex testing setup like Powermock to mock NoteDao's constructor,
// or use a DI framework.
//
// For this test, I will assume we CAN pass NoteDao for testability.
// If not, the test setup here is for an IDEAL NotesRepository.
// The actual implementation of NotesRepository in the previous step was:
// class NotesRepository { private val noteDao: NoteDao = NoteDao() ... }
// This makes mocking `noteDao` difficult without Powermock or changing the repository design.
//
// Re-adjusting the test to match the existing NotesRepository structure where NoteDao is internally instantiated.
// This means we can't directly mock `noteDao` in `NotesRepository` without more advanced tools.
//
// The current test code PASSES a mock `noteDao` to `NotesRepository`.
// This implies changing `NotesRepository` to:
// class NotesRepository(private val noteDao: NoteDao) { ... }
// If this change to NotesRepository is not desired, then these tests for NotesRepository
// cannot be written effectively with just Mockito for the *internally instantiated* DAO.
//
// The prompt for *this* subtask implies NotesRepository will have NoteDao passed in constructor.
// "The repository will need an instance of NoteDao. This can be passed in the constructor."
// So, I will proceed with the assumption that NotesRepository's constructor takes a NoteDao.
// I need to modify NotesRepository.kt to accept NoteDao in its constructor.
// This was not done in the previous subtask, so I'll do it now.
//
// The previous subtask created NotesRepository as:
// class NotesRepository {
//    private val noteDao: NoteDao = NoteDao()
// }
// I will modify this first, then the test will be valid.
// (This modification would typically be a separate step, but essential for this test)

// Note: The runTest block handles the CoroutineScope for suspend functions.
// Dispatchers.IO is implicitly handled by runTest for withContext(Dispatchers.IO).
// We are testing that the repository calls the DAO, and the DAO is responsible for its own threading.
// The repository's use of withContext is an implementation detail but good to be aware of.
// The test ensures the DAO methods are called, assuming they handle their own execution context correctly.
// The use of testDispatcher.scheduler.advanceUntilIdle() might be needed if there are delays or multiple context switches.
// For simple withContext calls, runTest usually handles it.
// Let's refine the suspend function tests to ensure the dispatcher is managed.
// runTest will automatically advance virtual time for StandardTestDispatcher.
// So explicit advanceUntilIdle is not strictly necessary for simple cases.

// The mock for SignalDatabase.runInTransaction is a common pattern for testing code
// that uses static utility methods for transactions.
// It ensures the transactional block is executed, allowing verification of DAO calls within it.
