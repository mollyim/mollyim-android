package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.profiles.EditMode; // Import EditMode
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import androidx.annotation.Nullable; // For Nullable noteId
import org.signal.core.util.logging.Log; // For logging

/**
 * Activity for editing your profile after you're already registered.
 */
public class EditProfileActivity extends PassphraseRequiredActivity implements ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  public static final int RESULT_BECOME_A_SUSTAINER = 12382;

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static final String START_AT_USERNAME = "start_at_username";
  public static final String START_AT_AVATAR   = "start_at_avatar";

  // Keep existing static constants for argument names in Fragment
  public static final String EXTRA_EDIT_MODE = "edit_mode";
  public static final String EXTRA_NOTE_ID = "note_id";

  private static final String TAG = Log.tag(EditProfileActivity.class);


  public static @NonNull Intent getIntent(@NonNull Context context) {
    // This is the existing method, presumably for self-profile editing.
    // Update it to also set the default edit mode.
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EXTRA_EDIT_MODE, EditMode.EDIT_SELF_PROFILE.name());
    return intent;
  }

  public static @NonNull Intent newNoteIntent(@NonNull Context context, @NonNull EditMode editMode, @Nullable Long noteId) {
    if (editMode != EditMode.EDIT_NOTE) {
      // Or assert, or allow other modes if this activity becomes more generic
      Log.w(TAG, "newNoteIntent called with mode " + editMode + " but only EDIT_NOTE is expected for this helper.");
    }
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EXTRA_EDIT_MODE, editMode.name());
    if (noteId != null) {
      intent.putExtra(EXTRA_NOTE_ID, noteId);
    }
    return intent;
  }

  public static @NonNull Intent getIntentForUsernameEdit(@NonNull Context context) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(START_AT_USERNAME, true);
    return intent;
  }

  public static @NonNull Intent getIntentForAvatarEdit(@NonNull Context context) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(START_AT_AVATAR, true);
    return intent;
  }

  @Override protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.edit_profile_activity);

    if (bundle == null) {
      Bundle extras = getIntent().getExtras();
      if (extras == null) {
        extras = new Bundle();
      }

      // Ensure default edit_mode is set if not provided by intent (e.g. old entry points)
      if (!extras.containsKey(EXTRA_EDIT_MODE)) {
        extras.putString(EXTRA_EDIT_MODE, EditMode.EDIT_SELF_PROFILE.name());
      }

      //noinspection ConstantConditions
      NavController navController = ((NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController();
      NavGraph      graph          = navController.getGraph();

      // Pass all extras (including our new ones) to the start destination of the graph
      navController.setGraph(graph, extras);

      // Existing navigation logic based on specific extras
      if (extras.getBoolean(START_AT_USERNAME, false)) {
        NavDirections action = EditProfileFragmentDirections.actionManageUsername();
        SafeNavigation.safeNavigate(navController, action);
      }

      if (extras != null && extras.getBoolean(START_AT_AVATAR, false)) {
        NavDirections action = EditProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null);
        SafeNavigation.safeNavigate(navController, action);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onReactWithAnyEmojiDialogDismissed() {
  }

  @Override
  public void onReactWithAnyEmojiSelected(@NonNull String emoji) {
    NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().getPrimaryNavigationFragment();
    Fragment        activeFragment  = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();

    if (activeFragment instanceof EmojiController) {
      ((EmojiController) activeFragment).onEmojiSelected(emoji);
    }
  }

  interface EmojiController {
    void onEmojiSelected(@NonNull String emoji);
  }
}
