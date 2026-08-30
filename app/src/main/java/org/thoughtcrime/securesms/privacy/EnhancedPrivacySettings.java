package org.thoughtcrime.securesms.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/** Centralized policy flags for the custom privacy layer. */
public final class EnhancedPrivacySettings {
  private static final String PREFS = "custom_privacy_settings";
  private static final String KEY_SECURE_RECENTS = "secure_recents";
  private static final String KEY_SCREENSHOT_BLOCK = "screenshot_block";
  private static final String KEY_HIDE_LOCK_SCREEN = "hide_lock_screen_content";
  private static final String KEY_HIDE_GROUP_NAMES = "hide_group_names";
  private static final String KEY_HIDE_NOTIFICATION_ACTIONS = "hide_notification_actions";
  private static final String KEY_NOTIFICATION_PRIVACY_MODE = "notification_privacy_mode";
  private EnhancedPrivacySettings() {}
  private static SharedPreferences prefs(@NonNull Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
  public static boolean isSecureRecentsEnabled(@NonNull Context c) { return prefs(c).getBoolean(KEY_SECURE_RECENTS, true); }
  public static void setSecureRecentsEnabled(@NonNull Context c, boolean v) { prefs(c).edit().putBoolean(KEY_SECURE_RECENTS, v).apply(); }
  public static boolean isScreenshotBlockingEnabled(@NonNull Context c) { return prefs(c).getBoolean(KEY_SCREENSHOT_BLOCK, true); }
  public static void setScreenshotBlockingEnabled(@NonNull Context c, boolean v) { prefs(c).edit().putBoolean(KEY_SCREENSHOT_BLOCK, v).apply(); }
  public static boolean isLockScreenContentHidden(@NonNull Context c) { return prefs(c).getBoolean(KEY_HIDE_LOCK_SCREEN, true); }
  public static void setLockScreenContentHidden(@NonNull Context c, boolean v) { prefs(c).edit().putBoolean(KEY_HIDE_LOCK_SCREEN, v).apply(); }
  public static boolean isGroupNameHidden(@NonNull Context c) { return prefs(c).getBoolean(KEY_HIDE_GROUP_NAMES, false); }
  public static void setGroupNameHidden(@NonNull Context c, boolean v) { prefs(c).edit().putBoolean(KEY_HIDE_GROUP_NAMES, v).apply(); }
  public static boolean areNotificationActionsHidden(@NonNull Context c) { return prefs(c).getBoolean(KEY_HIDE_NOTIFICATION_ACTIONS, true); }
  public static void setNotificationActionsHidden(@NonNull Context c, boolean v) { prefs(c).edit().putBoolean(KEY_HIDE_NOTIFICATION_ACTIONS, v).apply(); }

  @NonNull
  public static NotificationPrivacyMode getNotificationPrivacyMode(@NonNull Context c) {
    String stored = prefs(c).getString(KEY_NOTIFICATION_PRIVACY_MODE, NotificationPrivacyMode.CONTACT_ONLY.name());
    if (stored == null) return NotificationPrivacyMode.CONTACT_ONLY;
    try {
      return NotificationPrivacyMode.valueOf(stored);
    } catch (IllegalArgumentException e) {
      return NotificationPrivacyMode.CONTACT_ONLY;
    }
  }

  public static void setNotificationPrivacyMode(@NonNull Context c, @NonNull NotificationPrivacyMode mode) { prefs(c).edit().putString(KEY_NOTIFICATION_PRIVACY_MODE, mode.name()).apply(); }
}
