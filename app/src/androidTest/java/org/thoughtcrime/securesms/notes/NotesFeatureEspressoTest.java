package org.thoughtcrime.securesms.notes;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.LocaleUtil; // For context.getString

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription; // For FAB if using content description
import static org.hamcrest.Matchers.allOf; // If needed for complex matchers

// For Compose Test Tags, if MainActivity uses Compose directly for nav items
// import static androidx.compose.ui.test.junit4.createAndroidComposeRule
// import static androidx.compose.ui.test.onNodeWithTag

@RunWith(AndroidJUnit4.class)
public class NotesFeatureEspressoTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    // Helper to get string resources
    private String getString(int resId) {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getString(resId);
    }

    // Test Case 1: Create Note and Save Flow
    @Test
    public void testCreateNewNote_enterTitleAndContent_save_appearsInList() {
        // 1. Navigate to Notebook
        // Assuming main_navigation__notes_tab is a testTag applied to the "Notebook" navigation item
        // This part is tricky as it's Compose. If testTag is not available,
        // using withText for the navigation item might work if it's unique.
        // onView(ViewMatchers.withTagValue(is((Object)"main_navigation__notes_tab"))).perform(click()); // Ideal
        onView(withText(getString(R.string.main_navigation_notebook))).perform(click()); // Fallback

        // 2. Click New Note FAB
        // Assuming the FAB has a content description set or a testTag.
        // Let's use a placeholder content description for now.
        // The actual FAB icon changes based on mode, so content description might also change.
        // The FAB is in MainFloatingActionButtons.kt. When NOTES mode is active, it should have
        // R.string.notes_list_fragment__fab_new_note as content description.
        onView(withContentDescription(getString(R.string.notes_list_fragment__fab_new_note))).perform(click());

        // 3. In Note Editor (EditProfileActivity context)
        String noteTitle = "Test Note Title - Espresso " + System.currentTimeMillis(); // Unique title
        String noteContent = "Test Note Content - Espresso";

        onView(withId(R.id.manage_profile_name)).perform(typeText(noteTitle), closeSoftKeyboard());
        onView(withId(R.id.edit_note_content)).perform(typeText(noteContent), closeSoftKeyboard());

        // 4. Click Save
        onView(withId(R.id.action_save_profile_note)).perform(click());

        // 5. Verify in Notes List (ConversationListFragment context)
        // Need to ensure we are back on the notes list. The save action should finish EditProfileActivity.
        // Then, verify the item is displayed.
        onView(withText(noteTitle)).check(matches(isDisplayed()));
        // Optionally, check snippet too if it's reliably displayed
        // onView(allOf(withId(R.id.note_item_snippet), withText(startsWith(noteContent)))).check(matches(isDisplayed()));
    }

    // Test Case 2: Edit Existing Note and Save Flow
    @Test
    public void testEditExistingNote_modifyTitleAndContent_save_updatesInList() {
        // Prerequisite: Create a note to edit first.
        String originalTitle = "Original Test Note - Espresso " + System.currentTimeMillis();
        String originalContent = "Original content.";
        String updatedTitle = "Updated Test Note - Espresso " + System.currentTimeMillis();
        String updatedContent = "Updated content.";

        // 1. Navigate to Notebook
        onView(withText(getString(R.string.main_navigation_notebook))).perform(click());

        // Create the initial note (similar to TC1)
        onView(withContentDescription(getString(R.string.notes_list_fragment__fab_new_note))).perform(click());
        onView(withId(R.id.manage_profile_name)).perform(typeText(originalTitle), closeSoftKeyboard());
        onView(withId(R.id.edit_note_content)).perform(typeText(originalContent), closeSoftKeyboard());
        onView(withId(R.id.action_save_profile_note)).perform(click());
        onView(withText(originalTitle)).check(matches(isDisplayed())); // Confirm creation

        // 2. Click on the Existing Note
        // Using RecyclerViewActions for robustness, assuming R.id.list is the RecyclerView ID
        onView(withId(R.id.list))
            .perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(originalTitle)), click()));

        // 3. In Note Editor
        onView(withId(R.id.manage_profile_name)).perform(clearText(), typeText(updatedTitle), closeSoftKeyboard());
        onView(withId(R.id.edit_note_content)).perform(clearText(), typeText(updatedContent), closeSoftKeyboard());

        // 4. Click Save
        onView(withId(R.id.action_save_profile_note)).perform(click());

        // 5. Verify in Notes List
        onView(withText(updatedTitle)).check(matches(isDisplayed()));
        onView(withText(originalTitle)).check(doesNotExist());
    }
}
