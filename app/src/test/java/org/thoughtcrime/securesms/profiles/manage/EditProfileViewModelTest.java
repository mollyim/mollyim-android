package org.thoughtcrime.securesms.profiles.manage;

import android.app.Application;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.signal.core.util.concurrent.SignalExecutors; // For mocking if needed
import org.thoughtcrime.securesms.TestExecutors; // Assuming a test executor setup
import org.thoughtcrime.securesms.profiles.EditMode;
import org.thoughtcrime.securesms.notes.NoteEntity;
import org.thoughtcrime.securesms.notes.NotesRepository;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EditProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private Application mockApplication;
    @Mock
    private NotesRepository mockNotesRepository;
    @Mock
    private EditProfileRepository mockProfileRepository; // Existing repository
    @Mock
    private Observer<NoteEntity> mockNoteObserver;
    @Mock
    private Observer<Boolean> mockIsSavingObserver;

    @Captor
    private ArgumentCaptor<NoteEntity> noteEntityCaptor;

    private EditProfileViewModel viewModel;

    // Hold the static mock for SignalExecutors if used throughout tests
    private MockedStatic<SignalExecutors> signalExecutorsMock;


import org.junit.After;

// ... other imports

    @Before
    public void setUp() {
        // Mock SignalExecutors to run Runnables immediately for testing
        signalExecutorsMock = Mockito.mockStatic(SignalExecutors.class);
        signalExecutorsMock.when(() -> SignalExecutors.BOUNDED.execute(any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run(); // Execute the runnable immediately on the same thread
                return null;
            });
    }

    @After
    public void tearDown() {
        if (signalExecutorsMock != null) {
            signalExecutorsMock.close();
        }
    }

    // Test 1: Fetch Existing Note on Init
    @Test
    public void init_editNoteMode_withValidNoteId_fetchesAndExposesNote() {
        long testNoteId = 1L;
        NoteEntity sampleNote = new NoteEntity(testNoteId, "Title", "Content", null, 0L, 0L);
        when(mockNotesRepository.getNoteById(testNoteId)).thenReturn(sampleNote);

        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_NOTE, testNoteId, mockNotesRepository, mockProfileRepository);
        viewModel.getCurrentNote().observeForever(mockNoteObserver);

        // Verification using ArgumentCaptor if postValue is too fast for direct assert
        // or ensure TestExecutors make it synchronous
        // For now, let's assume direct check after init is enough due to InstantTaskExecutorRule
        // and if SignalExecutors are handled to be synchronous for the test.

        // Directly verify repository call (assuming it's called on a background thread managed by SignalExecutors)
        // If SignalExecutors.BOUNDED.execute is used in constructor, this verify needs to ensure that runnable has executed.
        // This often requires TestExecutors or a way to control thread execution.
        // For simplicity, if getNoteById is synchronous or its threading is internal to repo:
        verify(mockNotesRepository).getNoteById(testNoteId);

        // Then verify LiveData
        // Need to ensure the background task in constructor finishes.
        // This might require a slight delay or a more sophisticated way to handle background tasks in tests.
        // A common pattern is to use a CountDownLatch or TestCoroutineDispatcher for Kotlin.
        // For Java LiveData from a background thread, InstantTaskExecutorRule helps with observe,
        // but not necessarily with the background thread completion itself.
        // Let's assume for now the test setup makes this somewhat synchronous for verification.
        assertEquals(sampleNote, viewModel.getCurrentNote().getValue());
        verify(mockNoteObserver).onChanged(sampleNote);
    }

    // Test 2: Init with New Note (No Fetch)
    @Test
    public void init_editNoteMode_withNullNoteId_doesNotFetchAndNoteIsNull() {
        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_NOTE, null, mockNotesRepository, mockProfileRepository);
        viewModel.getCurrentNote().observeForever(mockNoteObserver);

        verify(mockNotesRepository, never()).getNoteById(anyLong());
        assertNull(viewModel.getCurrentNote().getValue());
        verify(mockNoteObserver).onChanged(null); // Initial value is null
    }

    // Test 3: saveCurrentNote - Create New Note
    @Test
    public void saveCurrentNote_newNote_callsCreateNoteOnRepository() {
        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_NOTE, null, mockNotesRepository, mockProfileRepository);
        viewModel.getIsSaving().observeForever(mockIsSavingObserver);

        String title = "New Title";
        String content = "New Content";
        when(mockNotesRepository.createNote(title, content, null)).thenReturn(123L); // Assume returns new ID

        viewModel.saveCurrentNote(title, content, null);

        verify(mockNotesRepository).createNote(title, content, null);
        // Verify isSaving states if used
        // verify(mockIsSavingObserver).onChanged(true);
        // verify(mockIsSavingObserver).onChanged(false);

        // Also verify that initialNoteId in ViewModel is updated
        // This requires initialNoteId to be accessible or tested via subsequent behavior
        // For now, we focus on repository interaction.
    }

    // Test 4: saveCurrentNote - Update Existing Note
    @Test
    public void saveCurrentNote_existingNote_callsUpdateNoteOnRepository() {
        long testNoteId = 1L;
        NoteEntity initialNote = new NoteEntity(testNoteId, "Old Title", "Old Content", null, 1000L, 1000L);

        // Simulate fetching existing note first
        when(mockNotesRepository.getNoteById(testNoteId)).thenReturn(initialNote);
        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_NOTE, testNoteId, mockNotesRepository, mockProfileRepository);
        assertEquals(initialNote, viewModel.getCurrentNote().getValue()); // Ensure it's loaded

        String updatedTitle = "Updated Title";
        String updatedContent = "Updated Content";

        viewModel.saveCurrentNote(updatedTitle, updatedContent, null);

        verify(mockNotesRepository).updateNote(noteEntityCaptor.capture());
        NoteEntity capturedNote = noteEntityCaptor.getValue();
        assertEquals(testNoteId, capturedNote.getId());
        assertEquals(updatedTitle, capturedNote.getTitle());
        assertEquals(updatedContent, capturedNote.getContent());
        assertTrue(capturedNote.getUpdatedAt() > initialNote.getUpdatedAt()); // Check timestamp updated

        // Verify LiveData is updated with the saved version
        assertEquals(capturedNote, viewModel.getCurrentNote().getValue());
    }

    // Test 5: saveCurrentNote - No Action if Not in EDIT_NOTE Mode
    @Test
    public void saveCurrentNote_notInEditNoteMode_doesNothing() {
        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_SELF_PROFILE, null, mockNotesRepository, mockProfileRepository);

        viewModel.saveCurrentNote("Title", "Content", null);

        verify(mockNotesRepository, never()).createNote(anyString(), anyString(), any());
        verify(mockNotesRepository, never()).updateNote(any(NoteEntity.class));
    }

    // Test 6: Ensure Profile Save Logic Not Triggered in EDIT_NOTE mode
    // This test depends on how saveProfile() is implemented.
    // If EditProfileViewModel has a public saveProfile() method:
    /*
    @Test
    public void saveProfile_inEditNoteMode_doesNotCallProfileRepository() {
        viewModel = new EditProfileViewModel(mockApplication, EditMode.EDIT_NOTE, 1L, mockNotesRepository, mockProfileRepository);
        // Assuming a method like save() or saveProfile() exists that would normally save profile data.
        // viewModel.saveProfileData(...); // Call the method that saves profile specific data
        // verify(mockProfileRepository, never()).updateProfile(...); // Verify profile repo not called
    }
    */
    // For now, this test is commented as saveProfile() logic isn't being added in this subtask.
    // The existing onSave in EditProfileActivity is generic and will need to be adapted
    // in a later step to call either saveCurrentNote or saveProfile based on mode.

    // @After
    // public void tearDownStaticMocks() {
    //     if (signalExecutorsMock != null) {
    //         signalExecutorsMock.close();
    //     }
    // }
}
