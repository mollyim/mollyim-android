package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import android.app.Application; // For Application context
import org.thoughtcrime.securesms.profiles.EditMode;
import org.thoughtcrime.securesms.notes.NoteEntity;
import org.thoughtcrime.securesms.notes.NotesRepository;
import org.thoughtcrime.securesms.database.NoteDao; // For instantiating NotesRepository in Factory

class EditProfileViewModel extends ViewModel {

  private static final String TAG = Log.tag(EditProfileViewModel.class);

  private final Application                          application; // To hold application context
  private final EditMode                             editMode;
  private       Long                                 initialNoteId; // Can be updated if a new note is created
  private final NotesRepository                      notesRepository;

  private final MutableLiveData<InternalAvatarState> internalAvatarState;
  private final MutableLiveData<ProfileName>         profileName;
  private final MutableLiveData<String>              username;
  private final MutableLiveData<String>              about;
  private final MutableLiveData<String>              aboutEmoji;
  private final LiveData<AvatarState>                avatarState;
  private final SingleLiveEvent<Event>               events;
  private final RecipientForeverObserver             observer;
  private final EditProfileRepository                repository;
  private final MutableLiveData<Optional<Badge>>     badge;

  // LiveData for current note
  private final MutableLiveData<NoteEntity> currentNote = new MutableLiveData<>(null);
  private final MutableLiveData<Boolean>    isSaving    = new MutableLiveData<>(false);


  private byte[] previousAvatar;

  public EditProfileViewModel(@NonNull Application application,
                              @NonNull EditMode editMode,
                              @Nullable Long initialNoteId,
                              @NonNull NotesRepository notesRepository,
                              @NonNull EditProfileRepository profileRepository) { // Assuming EditProfileRepository is the existing repo
    this.application         = application;
    this.editMode            = editMode;
    this.initialNoteId       = initialNoteId;
    this.notesRepository     = notesRepository;
    this.repository          = profileRepository; // Use the passed profile repository

    this.internalAvatarState    = new MutableLiveData<>();
    this.profileName            = new MutableLiveData<>();
    this.username               = new MutableLiveData<>();
    this.about                  = new MutableLiveData<>();
    this.aboutEmoji             = new MutableLiveData<>();
    this.events                 = new SingleLiveEvent<>();
    this.repository             = new EditProfileRepository();
    this.badge                  = new DefaultValueLiveData<>(Optional.empty());
    this.observer               = this::onRecipientChanged;
    this.avatarState            = LiveDataUtil.combineLatest(Recipient.self().live().getLiveData(), internalAvatarState, (self, state) -> new AvatarState(state, self));

    if (this.editMode == EditMode.EDIT_NOTE && this.initialNoteId != null) {
      SignalExecutors.BOUNDED.execute(() -> {
        NoteEntity note = this.notesRepository.getNoteById(this.initialNoteId);
        currentNote.postValue(note);
      });
    } else if (this.editMode == EditMode.EDIT_SELF_PROFILE) {
      SignalExecutors.BOUNDED.execute(() -> {
        onRecipientChanged(Recipient.self().fresh());
        RetrieveProfileJob.enqueue(Recipient.self().getId());
      });
      Recipient.self().live().observeForever(observer);
    }
  }

  public @NonNull LiveData<NoteEntity> getCurrentNote() {
    return currentNote;
  }

  public @NonNull LiveData<Boolean> getIsSaving() {
    return isSaving;
  }

  public @NonNull LiveData<AvatarState> getAvatar() {
    return Transformations.distinctUntilChanged(avatarState);
  }

  public @NonNull LiveData<ProfileName> getProfileName() {
    return profileName;
  }

  public @NonNull LiveData<String> getUsername() {
    return username;
  }

  public @NonNull LiveData<String> getAbout() {
    return about;
  }

  public @NonNull LiveData<String> getAboutEmoji() {
    return aboutEmoji;
  }

  public @NonNull LiveData<Optional<Badge>> getBadge() {
    return badge;
  }

  public @NonNull LiveData<Event> getEvents() {
    return events;
  }

  public Single<UsernameRepository.UsernameDeleteResult> deleteUsername() {
    return UsernameRepository.deleteUsernameAndLink().observeOn(AndroidSchedulers.mainThread());
  }

  public boolean isRegisteredAndUpToDate() {
    return !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.getApplication()) && SignalStore.account().isRegistered() && !SignalStore.misc().isClientDeprecated();
  }

  public boolean isDeprecated() {
    return SignalStore.misc().isClientDeprecated();
  }

  public void onAvatarSelected(@NonNull Context context, @Nullable Media media) {
    previousAvatar = internalAvatarState.getValue() != null ? internalAvatarState.getValue().getAvatar() : null;

    if (media == null) {
      internalAvatarState.postValue(InternalAvatarState.loading(null));
      repository.clearAvatar(context, result -> {
        switch (result) {
          case SUCCESS:
            internalAvatarState.postValue(InternalAvatarState.loaded(null));
            previousAvatar = null;
            break;
          case FAILURE_NETWORK:
            internalAvatarState.postValue(InternalAvatarState.loaded(previousAvatar));
            events.postValue(Event.AVATAR_NETWORK_FAILURE);
            break;
        }
      });
    } else {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          InputStream stream = BlobProvider.getInstance().getStream(context, media.getUri());
          byte[]      data   = StreamUtil.readFully(stream);

          internalAvatarState.postValue(InternalAvatarState.loading(data));

          repository.setAvatar(context, data, media.getContentType(), result -> {
            switch (result) {
              case SUCCESS:
                internalAvatarState.postValue(InternalAvatarState.loaded(data));
                previousAvatar = data;
                break;
              case FAILURE_NETWORK:
                internalAvatarState.postValue(InternalAvatarState.loaded(previousAvatar));
                events.postValue(Event.AVATAR_NETWORK_FAILURE);
                break;
            }
          });
        } catch (IOException e) {
          Log.w(TAG, "Failed to save avatar!", e);
          events.postValue(Event.AVATAR_DISK_FAILURE);
        }
      });
    }
  }

  public boolean canRemoveAvatar() {
    return internalAvatarState.getValue() != null;
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    profileName.postValue(recipient.getProfileName());
    username.postValue(SignalStore.account().getUsername());
    about.postValue(recipient.getAbout());
    aboutEmoji.postValue(recipient.getAboutEmoji());
    badge.postValue(Optional.ofNullable(recipient.getFeaturedBadge()));
    renderAvatar(AvatarHelper.getSelfProfileAvatarStream(AppDependencies.getApplication()));
  }

  private void renderAvatar(@Nullable StreamDetails details) {
    if (details != null) {
      try {
        internalAvatarState.postValue(InternalAvatarState.loaded(StreamUtil.readFully(details.getStream())));
      } catch (IOException e) {
        Log.w(TAG, "Failed to read avatar!");
        internalAvatarState.postValue(InternalAvatarState.none());
      }
    } else {
      internalAvatarState.postValue(InternalAvatarState.none());
    }
  }

  @Override
  protected void onCleared() {
    Recipient.self().live().removeForeverObserver(observer);
  }

  public final static class AvatarState {
    private final InternalAvatarState internalAvatarState;
    private final Recipient           self;

    public AvatarState(@NonNull InternalAvatarState internalAvatarState,
                       @NonNull Recipient self)
    {
      this.internalAvatarState = internalAvatarState;
      this.self                = self;
    }

    public @Nullable byte[] getAvatar() {
      return internalAvatarState.avatar;
    }

    public @NonNull LoadingState getLoadingState() {
      return internalAvatarState.loadingState;
    }

    public @NonNull Recipient getSelf() {
      return self;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final AvatarState that = (AvatarState) o;
      return Objects.equals(internalAvatarState, that.internalAvatarState) && Objects.equals(self, that.self);
    }

    @Override
    public int hashCode() {
      return Objects.hash(internalAvatarState, self);
    }
  }

  private final static class InternalAvatarState {
    private final byte[]       avatar;
    private final LoadingState loadingState;

    public InternalAvatarState(@Nullable byte[] avatar, @NonNull LoadingState loadingState) {
      this.avatar       = avatar;
      this.loadingState = loadingState;
    }

    private static @NonNull InternalAvatarState none() {
      return new InternalAvatarState(null, LoadingState.LOADED);
    }

    private static @NonNull InternalAvatarState loaded(@Nullable byte[] avatar) {
      return new InternalAvatarState(avatar, LoadingState.LOADED);
    }

    private static @NonNull InternalAvatarState loading(@Nullable byte[] avatar) {
      return new InternalAvatarState(avatar, LoadingState.LOADING);
    }

    public @Nullable byte[] getAvatar() {
      return avatar;
    }

    public LoadingState getLoadingState() {
      return loadingState;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final InternalAvatarState that = (InternalAvatarState) o;
      return Arrays.equals(avatar, that.avatar) && loadingState == that.loadingState;
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(loadingState);
      result = 31 * result + Arrays.hashCode(avatar);
      return result;
    }
  }

  public enum LoadingState {
    LOADING, LOADED
  }

  enum Event {
    AVATAR_NETWORK_FAILURE, AVATAR_DISK_FAILURE, NOTE_SAVE_SUCCESS, NOTE_SAVE_FAILURE // Example new events
  }

  public void saveCurrentNote(@NonNull String title, @NonNull String content, @Nullable Long colorId) {
    if (editMode != EditMode.EDIT_NOTE) {
      Log.w(TAG, "saveCurrentNote called in incorrect mode: " + editMode);
      return;
    }

    isSaving.setValue(true);
    SignalExecutors.BOUNDED.execute(() -> {
      NoteEntity noteToSave = currentNote.getValue();
      long noteIdToUpdateOrInsert = (noteToSave != null) ? noteToSave.getId() : (initialNoteId != null ? initialNoteId : 0L);


      if (noteIdToUpdateOrInsert != 0L && noteToSave != null) { // Existing note
        NoteEntity updatedNote = new NoteEntity(
            noteToSave.getId(),
            title,
            content,
            colorId != null ? colorId : noteToSave.getColorId(),
            noteToSave.getCreatedAt(),
            System.currentTimeMillis()
        );
        notesRepository.updateNote(updatedNote);
        currentNote.postValue(updatedNote); // Update LiveData with saved version
        // events.postValue(Event.NOTE_SAVE_SUCCESS);
      } else { // New note
        long newNoteId = notesRepository.createNote(title, content, colorId);
        if (newNoteId > 0) {
          this.initialNoteId = newNoteId; // Update initialNoteId
          // Optionally re-fetch and set to currentNote if further edits on this screen are expected
          // NoteEntity newCreatedNote = notesRepository.getNoteById(newNoteId);
          // currentNote.postValue(newCreatedNote);
          // events.postValue(Event.NOTE_SAVE_SUCCESS);
        } else {
          // events.postValue(Event.NOTE_SAVE_FAILURE);
        }
      }
      isSaving.postValue(false);
    });
  }


  static class Factory implements ViewModelProvider.Factory { // Implement Factory, not extend NewInstanceFactory for custom constructor
    private final Application application;
    private final EditMode    editMode;
    private final Long        noteId;

    public Factory(@NonNull Application application, @NonNull EditMode editMode, @Nullable Long noteId) {
      this.application = application;
      this.editMode = editMode;
      this.noteId = noteId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      if (modelClass.isAssignableFrom(EditProfileViewModel.class)) {
        NotesRepository notesRepository = new NotesRepository(new NoteDao()); // Create NotesRepository here
        EditProfileRepository profileRepository = new EditProfileRepository(); // Create existing repo here
        //noinspection unchecked
        return (T) new EditProfileViewModel(application, editMode, noteId, notesRepository, profileRepository);
      }
      throw new IllegalArgumentException("Unknown ViewModel class");
    }
  }
}
