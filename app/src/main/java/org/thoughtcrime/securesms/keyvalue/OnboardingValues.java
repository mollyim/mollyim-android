package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class OnboardingValues extends SignalStoreValues {

  private static final String SHOW_STORIES         = "onboarding.stories_molly";
  private static final String SHOW_APPEARANCE      = "onboarding.appearance";
  private static final String SHOW_ADD_PHOTO       = "onboarding.add_photo";
  private static final String SHOW_SET_UP_USERNAME = "onboarding.set_up_username";

  OnboardingValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putBoolean(SHOW_STORIES, true);
    putBoolean(SHOW_APPEARANCE, true);
    putBoolean(SHOW_ADD_PHOTO, true);
    putBoolean(SHOW_SET_UP_USERNAME, true);
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public void clearAll() {
    setShowStories(false);
    setShowAppearance(false);
    setShowAddPhoto(false);
    setShowSetUpUsername(false);
  }

  public boolean hasOnboarding(@NonNull Context context) {
    return shouldShowStories() ||
           shouldShowAppearance() ||
           shouldShowAddPhoto() ||
           shouldShowSetUpUsername();
  }

  public void setShowStories(boolean value) {
    putBoolean(SHOW_STORIES, value);
  }

  public boolean shouldShowStories() {
    return getBoolean(SHOW_STORIES, false);
  }

  public void setShowAppearance(boolean value) {
    putBoolean(SHOW_APPEARANCE, value);
  }

  public boolean shouldShowAppearance() {
    return getBoolean(SHOW_APPEARANCE, false);
  }

  public void setShowAddPhoto(boolean value) {
    putBoolean(SHOW_ADD_PHOTO, value);
  }

  public boolean shouldShowAddPhoto() {
    return getBoolean(SHOW_ADD_PHOTO, false);
  }

  public void setShowSetUpUsername(boolean value) {
    putBoolean(SHOW_SET_UP_USERNAME, value);
  }

  public boolean shouldShowSetUpUsername() {
    return getBoolean(SHOW_SET_UP_USERNAME, false);
  }
}
