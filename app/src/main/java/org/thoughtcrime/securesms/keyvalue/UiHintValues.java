package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public class UiHintValues extends SignalStoreValues {

  private static final int NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD = 3;

  private static final String HAS_SEEN_GROUP_SETTINGS_MENU_TOAST       = "uihints.has_seen_group_settings_menu_toast";
  private static final String HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE   = "uihints.has_confirmed_delete_for_everyone_once";
  private static final String HAS_SET_OR_SKIPPED_USERNAME_CREATION     = "uihints.has_set_or_skipped_username_creation";
  private static final String NEVER_DISPLAY_PULL_TO_FILTER_TIP         = "uihints.never_display_pull_to_filter_tip";
  private static final String HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE    = "uihints.has_seen_scheduled_messages_info_once";
  private static final String HAS_SEEN_TEXT_FORMATTING_ALERT           = "uihints.text_formatting.has_seen_alert";
  private static final String HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT     = "uihints.edit_message.has_not_seen_beta_alert";
  private static final String HAS_SEEN_SAFETY_NUMBER_NUX               = "uihints.has_seen_safety_number_nux";
  private static final String DISMISSED_BATTERY_SAVER_PROMPT           = "uihints.declined_battery_saver_prompt";
  private static final String LAST_BATTERY_SAVER_PROMPT                = "uihints.last_battery_saver_prompt";
  private static final String HAS_COMPLETED_USERNAME_ONBOARDING        = "uihints.has_completed_username_onboarding";
  private static final String HAS_SEEN_DOUBLE_TAP_EDIT_EDUCATION_SHEET = "uihints.has_seen_double_tap_edit_education_sheet";
  private static final String DISMISSED_CONTACTS_PERMISSION_BANNER     = "uihints.dismissed_contacts_permission_banner";
  private static final String HAS_SEEN_DELETE_SYNC_EDUCATION_SHEET     = "uihints.has_seen_delete_sync_education_sheet";
  private static final String LAST_SUPPORT_VERSION_SEEN                = "uihints.last_support_version_seen";
  private static final String HAS_EVER_ENABLED_REMOTE_BACKUPS          = "uihints.has_ever_enabled_remote_backups";
  private static final String HAS_SEEN_CHAT_FOLDERS_EDUCATION_SHEET    = "uihints.has_seen_chat_folders_education_sheet";
  private static final String HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET  = "uihints.has_seen_link_device_qr_education_sheet";
  private static final String HAS_DISMISSED_SAVE_STORAGE_WARNING       = "uihints.has_dismissed_save_storage_warning";

  UiHintValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    markHasSeenGroupSettingsMenuToast();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(NEVER_DISPLAY_PULL_TO_FILTER_TIP, HAS_COMPLETED_USERNAME_ONBOARDING, HAS_SEEN_TEXT_FORMATTING_ALERT, HAS_EVER_ENABLED_REMOTE_BACKUPS);
  }

  public void markHasSeenGroupSettingsMenuToast() {
    putBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, true);
  }

  public boolean hasSeenGroupSettingsMenuToast() {
    return getBoolean(HAS_SEEN_GROUP_SETTINGS_MENU_TOAST, false);
  }

  public void markHasSeenScheduledMessagesInfoSheet() {
    putBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, true);
  }

  public boolean hasSeenScheduledMessagesInfoSheet() {
    return getBoolean(HAS_SEEN_SCHEDULED_MESSAGES_INFO_ONCE, false);
  }

  public void markHasConfirmedDeleteForEveryoneOnce() {
    putBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, true);
  }

  public boolean hasConfirmedDeleteForEveryoneOnce() {
    return getBoolean(HAS_CONFIRMED_DELETE_FOR_EVERYONE_ONCE, false);
  }

  public boolean hasSetOrSkippedUsernameCreation() {
    return getBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, false);
  }

  public void markHasSetOrSkippedUsernameCreation() {
    putBoolean(HAS_SET_OR_SKIPPED_USERNAME_CREATION, true);
  }

  public void setHasCompletedUsernameOnboarding(boolean value) {
    putBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, value);
  }

  public boolean hasCompletedUsernameOnboarding() {
    return getBoolean(HAS_COMPLETED_USERNAME_ONBOARDING, false);
  }

  public void resetNeverDisplayPullToRefreshCount() {
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0);
  }

  public boolean canDisplayPullToFilterTip() {
    return getNeverDisplayPullToFilterTip() < NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD;
  }

  public void incrementNeverDisplayPullToFilterTip() {
    int inc = Math.min(NEVER_DISPLAY_PULL_TO_FILTER_TIP_THRESHOLD, getNeverDisplayPullToFilterTip() + 1);
    putInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, inc);
  }

  private int getNeverDisplayPullToFilterTip() {
    return getInteger(NEVER_DISPLAY_PULL_TO_FILTER_TIP, 0);
  }

  public boolean hasNotSeenTextFormattingAlert() {
    return getBoolean(HAS_SEEN_TEXT_FORMATTING_ALERT, true);
  }

  public void markHasSeenTextFormattingAlert() {
    putBoolean(HAS_SEEN_TEXT_FORMATTING_ALERT, false);
  }

  public boolean hasNotSeenEditMessageBetaAlert() {
    return getBoolean(HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT, true);
  }

  public void markHasSeenEditMessageBetaAlert() {
    putBoolean(HAS_NOT_SEEN_EDIT_MESSAGE_BETA_ALERT, false);
  }

  public boolean hasSeenSafetyNumberUpdateNux() {
    return getBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, false);
  }

  public void markHasSeenSafetyNumberUpdateNux() {
    putBoolean(HAS_SEEN_SAFETY_NUMBER_NUX, true);
  }

  public void markDismissedBatterySaverPrompt() {
    putBoolean(DISMISSED_BATTERY_SAVER_PROMPT, true);
  }

  public boolean hasDismissedBatterySaverPrompt() {
    return getBoolean(DISMISSED_BATTERY_SAVER_PROMPT, false);
  }

  public long getLastBatterySaverPrompt() {
    return getLong(LAST_BATTERY_SAVER_PROMPT, 0);
  }

  public void setLastBatterySaverPrompt(long time) {
    putLong(LAST_BATTERY_SAVER_PROMPT, time);
  }

  public void setHasSeenDoubleTapEditEducationSheet(boolean seen) {
    putBoolean(HAS_SEEN_DOUBLE_TAP_EDIT_EDUCATION_SHEET, seen);
  }

  public boolean getHasSeenDoubleTapEditEducationSheet() {
    return getBoolean(HAS_SEEN_DOUBLE_TAP_EDIT_EDUCATION_SHEET, false);
  }

  public void markDismissedContactsPermissionBanner() {
    putBoolean(DISMISSED_CONTACTS_PERMISSION_BANNER, true);
  }

  public boolean getDismissedContactsPermissionBanner() {
    return getBoolean(DISMISSED_CONTACTS_PERMISSION_BANNER, false);
  }

  public void setHasSeenDeleteSyncEducationSheet(boolean seen) {
    putBoolean(HAS_SEEN_DELETE_SYNC_EDUCATION_SHEET, seen);
  }

  public boolean getHasSeenDeleteSyncEducationSheet() {
    return getBoolean(HAS_SEEN_DELETE_SYNC_EDUCATION_SHEET, false);
  }

  /**
   * @return the last version of the support article for delayed notifications that users have seen. Versions are increased in a remote config.
   */
  public int getLastSupportVersionSeen() {
    return getInteger(LAST_SUPPORT_VERSION_SEEN, 0);
  }

  /**
   * Sets the version number of the support article that users see if they have device-specific notifications issues
   */
  public void setLastSupportVersionSeen(int version) {
    putInteger(LAST_SUPPORT_VERSION_SEEN, version);
  }

  public void markHasEverEnabledRemoteBackups() {
    putBoolean(HAS_EVER_ENABLED_REMOTE_BACKUPS, true);
  }

  public boolean getHasEverEnabledRemoteBackups() {
    return getBoolean(HAS_EVER_ENABLED_REMOTE_BACKUPS, false);
  }

  public void setHasSeenChatFoldersEducationSheet(boolean seen) {
    putBoolean(HAS_SEEN_CHAT_FOLDERS_EDUCATION_SHEET, seen);
  }

  public boolean getHasSeenChatFoldersEducationSheet() {
    return getBoolean(HAS_SEEN_CHAT_FOLDERS_EDUCATION_SHEET, false);
  }

  public void markHasSeenLinkDeviceQrEducationSheet() {
    putBoolean(HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET, true);
  }

  public boolean hasSeenLinkDeviceQrEducationSheet() {
    return getBoolean(HAS_SEEN_LINK_DEVICE_QR_EDUCATION_SHEET, false);
  }

  public boolean hasDismissedSaveStorageWarning() {
    return getBoolean(HAS_DISMISSED_SAVE_STORAGE_WARNING, false);
  }

  public void markDismissedSaveStorageWarning() {
    putBoolean(HAS_DISMISSED_SAVE_STORAGE_WARNING, true);
  }
}
