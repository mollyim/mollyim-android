package org.thoughtcrime.securesms.util;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener;
import org.thoughtcrime.securesms.backup.proto.SharedPreference;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SettingsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.net.ProxyType;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TextSecurePreferences {

  private static final String TAG = Log.tag(TextSecurePreferences.class);

  public  static final String THEME_PREF                       = "pref_theme";
  public  static final String DYNAMIC_COLORS_ENABLED           = "pref_dynamic_colors";
  public  static final String LANGUAGE_PREF                    = "pref_language";

  private static final String LAST_VERSION_CODE_PREF           = "last_version_code";
  public  static final String RINGTONE_PREF                    = "pref_key_ringtone";
  public  static final String VIBRATE_PREF                     = "pref_key_vibrate";
  private static final String NOTIFICATION_PREF                = "pref_key_enable_notifications";
  public  static final String LED_COLOR_PREF                   = "pref_led_color";
  public  static final String LED_BLINK_PREF                   = "pref_led_blink";
  private static final String LED_BLINK_PREF_CUSTOM            = "pref_led_blink_custom";
  public  static final String SCREEN_SECURITY_PREF             = "pref_screen_security";
  private static final String ENTER_SENDS_PREF                 = "pref_enter_sends";
  private static final String PROMPTED_PUSH_REGISTRATION_PREF  = "pref_prompted_push_registration";
  private static final String PROMPTED_OPTIMIZE_DOZE_PREF      = "pref_prompted_optimize_doze";
  public  static final String DIRECTORY_FRESH_TIME_PREF        = "pref_directory_refresh_time";
  public  static final String REFRESH_FCM_TOKEN_PREF           = "pref_refresh_fcm_token";
  public  static final String UPDATE_APK_ENABLED               = "pref_update_apk_enabled";
  public  static final String UPDATE_APK_INCLUDE_BETA          = "pref_update_apk_include_beta";
  private static final String UPDATE_APK_REFRESH_TIME_PREF     = "pref_update_apk_refresh_time";
  private static final String SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time";

  private static final String IN_THREAD_NOTIFICATION_PREF      = "pref_key_inthread_notifications";
  public  static final String MESSAGE_BODY_TEXT_SIZE_PREF      = "pref_message_body_text_size";

  public  static final String REPEAT_ALERTS_PREF               = "pref_repeat_alerts";
  public  static final String NOTIFICATION_PRIVACY_PREF        = "pref_notification_privacy";
  public  static final String NOTIFICATION_PRIORITY_PREF       = "pref_notification_priority";
  public  static final String NEW_CONTACTS_NOTIFICATIONS       = "pref_enable_new_contacts_notifications";

  public  static final String MEDIA_DOWNLOAD_MOBILE_PREF       = "pref_media_download_mobile";
  public  static final String MEDIA_DOWNLOAD_WIFI_PREF         = "pref_media_download_wifi";
  public  static final String MEDIA_DOWNLOAD_ROAMING_PREF      = "pref_media_download_roaming";

  public  static final String SYSTEM_EMOJI_PREF                = "pref_system_emoji";
  public  static final String DIRECT_CAPTURE_CAMERA_ID         = "pref_direct_capture_camera_id";
  public  static final String ALWAYS_RELAY_CALLS_PREF          = "pref_turn_only";
  public  static final String READ_RECEIPTS_PREF               = "pref_read_receipts";
  public  static final String INCOGNITO_KEYBORAD_PREF          = "pref_incognito_keyboard";
  public  static final String UNAUTHORIZED_RECEIVED            = "pref_unauthorized_received";
  private static final String SUCCESSFUL_DIRECTORY_PREF        = "pref_successful_directory";

  private static final String DATABASE_ENCRYPTED_SECRET     = "pref_database_encrypted_secret";
  private static final String DATABASE_UNENCRYPTED_SECRET   = "pref_database_unencrypted_secret";
  private static final String ATTACHMENT_ENCRYPTED_SECRET   = "pref_attachment_encrypted_secret";
  private static final String ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret";

  public static final String CALL_NOTIFICATIONS_PREF = "pref_call_notifications";
  public static final String CALL_RINGTONE_PREF      = "pref_call_ringtone";
  public static final String CALL_VIBRATE_PREF       = "pref_call_vibrate";

  public  static final String BACKUP                      = "pref_backup";
  public  static final String BACKUP_ENABLED              = "pref_backup_enabled";
  private static final String BACKUP_PASSPHRASE           = "pref_backup_passphrase";
  private static final String ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase";
  private static final String BACKUP_TIME                 = "pref_backup_next_time";
  private static final String BACKUP_INTERVAL             = "pref_backup_interval";
  private static final String BACKUP_MAX_FILES            = "pref_backup_max_files";

  public  static final String PASSPHRASE_LOCK               = "pref_passphrase_lock";
  public  static final String PASSPHRASE_LOCK_TIMEOUT       = "pref_passphrase_lock_timeout";
  public  static final String PASSPHRASE_LOCK_TRIGGER       = "pref_passphrase_lock_trigger";
  public  static final String PASSPHRASE_LOCK_NOTIFICATIONS = "pref_passphrase_lock_notifications";
  public  static final String BIOMETRIC_SCREEN_LOCK         = "pref_biometric_screen_lock";

  private static final String NETWORK_CONFIG_SEEN = "pref_network_config_seen";

  public  static final String PROXY_TYPE       = "pref_proxy_type";
  public  static final String PROXY_SOCKS_HOST = "pref_proxy_socks_host";
  public  static final String PROXY_SOCKS_PORT = "pref_proxy_socks_port";

  @Deprecated
  public static final  String REGISTRATION_LOCK_PREF_V1                = "pref_registration_lock";
  @Deprecated
  private static final String REGISTRATION_LOCK_PIN_PREF_V1            = "pref_registration_lock_pin";

  private static final String REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS = "pref_registration_lock_last_reminder_time_post_kbs";
  private static final String REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL      = "pref_registration_lock_next_reminder_interval";

  public  static final String SERVICE_OUTAGE         = "pref_service_outage";
  private static final String LAST_OUTAGE_CHECK_TIME = "pref_last_outage_check_time";

  private static final String LAST_FULL_CONTACT_SYNC_TIME = "pref_last_full_contact_sync_time";
  private static final String NEEDS_FULL_CONTACT_SYNC     = "pref_needs_full_contact_sync";

  public  static final String LOG_ENABLED            = "pref_log_enabled";
  private static final String LOG_ENCRYPTED_SECRET   = "pref_log_encrypted_secret";
  private static final String LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret";

  private static final String NOTIFICATION_CHANNEL_VERSION          = "pref_notification_channel_version";
  private static final String NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version";

  private static final String NEEDS_MESSAGE_PULL = "pref_needs_message_pull";

  private static final String UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF = "pref_unidentified_access_certificate_rotation_time";
  public  static final String UNIVERSAL_UNIDENTIFIED_ACCESS                      = "pref_universal_unidentified_access";
  public  static final String SHOW_UNIDENTIFIED_DELIVERY_INDICATORS              = "pref_show_unidentifed_delivery_indicators";

  public static final String TYPING_INDICATORS = "pref_typing_indicators";

  private static final String BLOCK_UNKNOWN = "pref_block_unknown";

  public static final String LINK_PREVIEWS = "pref_link_previews";

  private static final String MEDIA_KEYBOARD_MODE = "pref_media_keyboard_mode";
  public  static final String RECENT_STORAGE_KEY  = "pref_recent_emoji2";

  private static final String JOB_MANAGER_VERSION = "pref_job_manager_version";

  private static final String APP_MIGRATION_VERSION = "pref_app_migration_version";

  public  static final String FIRST_INSTALL_VERSION = "pref_first_install_version";

  private static final String HAS_SEEN_SWIPE_TO_REPLY = "pref_has_seen_swipe_to_reply";

  private static final String HAS_SEEN_VIDEO_RECORDING_TOOLTIP = "camerax.fragment.has.dismissed.video.recording.tooltip";

  private static final String GOOGLE_MAP_TYPE = "pref_google_map_type";

  public static String getGoogleMapType(Context context) {
    return getStringPreference(context, GOOGLE_MAP_TYPE, "normal");
  }

  public static void setGoogleMapType(Context context, String value) {
    setStringPreference(context, GOOGLE_MAP_TYPE, value);
  }

  private static final String[] booleanPreferencesToBackup = {SCREEN_SECURITY_PREF,
                                                              INCOGNITO_KEYBORAD_PREF,
                                                              ALWAYS_RELAY_CALLS_PREF,
                                                              READ_RECEIPTS_PREF,
                                                              TYPING_INDICATORS,
                                                              SHOW_UNIDENTIFIED_DELIVERY_INDICATORS,
                                                              UNIVERSAL_UNIDENTIFIED_ACCESS,
                                                              NOTIFICATION_PREF,
                                                              VIBRATE_PREF,
                                                              IN_THREAD_NOTIFICATION_PREF,
                                                              CALL_NOTIFICATIONS_PREF,
                                                              CALL_VIBRATE_PREF,
                                                              NEW_CONTACTS_NOTIFICATIONS,
                                                              SYSTEM_EMOJI_PREF,
                                                              ENTER_SENDS_PREF};

  private static final String[] stringPreferencesToBackup = {LED_COLOR_PREF,
                                                             LED_BLINK_PREF,
                                                             REPEAT_ALERTS_PREF,
                                                             NOTIFICATION_PRIVACY_PREF,
                                                             THEME_PREF,
                                                             LANGUAGE_PREF,
                                                             MESSAGE_BODY_TEXT_SIZE_PREF};

  private static final String[] stringSetPreferencesToBackup = {MEDIA_DOWNLOAD_MOBILE_PREF,
                                                                MEDIA_DOWNLOAD_WIFI_PREF,
                                                                MEDIA_DOWNLOAD_ROAMING_PREF};

  private static final String[] booleanPreferencesToBackupMolly = {
      LOG_ENABLED,
      UPDATE_APK_ENABLED,
      UPDATE_APK_INCLUDE_BETA,
      BLOCK_UNKNOWN,
      BIOMETRIC_SCREEN_LOCK,
      PASSPHRASE_LOCK_NOTIFICATIONS,
      DYNAMIC_COLORS_ENABLED,
  };

  private static final String[] stringSetPreferencesToBackupMolly = {PASSPHRASE_LOCK_TRIGGER};

  private static final String[] integerPreferencesToBackupMolly = {PASSPHRASE_LOCK_TIMEOUT};

  public static long getPreferencesToSaveToBackupCount(@NonNull Context context) {
    SharedPreferences preferences = getSharedPreferences(context);
    long              count       = 0;

    for (String booleanPreference : booleanPreferencesToBackup) {
      if (preferences.contains(booleanPreference)) {
        count++;
      }
    }

    for (String stringPreference : stringPreferencesToBackup) {
      if (preferences.contains(stringPreference)) {
        count++;
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackup) {
      if (preferences.contains(stringSetPreference)) {
        count++;
      }
    }

    for (String booleanPreference : booleanPreferencesToBackupMolly) {
      if (preferences.contains(booleanPreference)) {
        count++;
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackupMolly) {
      if (preferences.contains(stringSetPreference)) {
        count++;
      }
    }

    for (String integerPreference : integerPreferencesToBackupMolly) {
      if (preferences.contains(integerPreference)) {
        count++;
      }
    }

    return count;
  }

  public static List<SharedPreference> getPreferencesToSaveToBackup(@NonNull Context context) {
    SharedPreferences      preferences  = getSharedPreferences(context);
    List<SharedPreference> backupProtos = new ArrayList<>();
    String                 defaultFile  = BuildConfig.SIGNAL_PACKAGE_NAME + "_preferences";

    for (String booleanPreference : booleanPreferencesToBackup) {
      if (preferences.contains(booleanPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(booleanPreference)
                                             .booleanValue(preferences.getBoolean(booleanPreference, false))
                                             .build());
      }
    }

    for (String stringPreference : stringPreferencesToBackup) {
      if (preferences.contains(stringPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(stringPreference)
                                             .value_(preferences.getString(stringPreference, null))
                                             .build());
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackup) {
      if (preferences.contains(stringSetPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(defaultFile)
                                             .key(stringSetPreference)
                                             .isStringSetValue(true)
                                             .stringSetValue(new ArrayList<>(preferences.getStringSet(stringSetPreference, Collections.emptySet())))
                                             .build());
      }
    }

    for (String booleanPreference : booleanPreferencesToBackupMolly) {
      if (preferences.contains(booleanPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(SecurePreferenceManager.getSecurePreferencesName())
                                             .key(booleanPreference)
                                             .booleanValue(preferences.getBoolean(booleanPreference, false))
                                             .build());
      }
    }

    for (String stringSetPreference : stringSetPreferencesToBackupMolly) {
      if (preferences.contains(stringSetPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(SecurePreferenceManager.getSecurePreferencesName())
                                             .key(stringSetPreference)
                                             .isStringSetValue(true)
                                             .stringSetValue(new ArrayList<>(preferences.getStringSet(stringSetPreference, Collections.emptySet())))
                                             .build());
      }
    }

    for (String integerPreference : integerPreferencesToBackupMolly) {
      if (preferences.contains(integerPreference)) {
        backupProtos.add(new SharedPreference.Builder()
                                             .file_(SecurePreferenceManager.getSecurePreferencesName())
                                             .key(integerPreference)
                                             .integerValue(preferences.getInt(integerPreference, 0))
                                             .build());
      }
    }

    return backupProtos;
  }

  public static void onPostBackupRestore(@NonNull Context context) {
    if (NotificationChannels.supported()) {
      NotificationChannels.getInstance().updateMessageVibrate(SignalStore.settings().isMessageVibrateEnabled());
    }

    if (!isLogEnabled(context)) {
      Log.setLogging(false);
      Log.wipeLogs();
    }

    if (isUpdateApkEnabled(context)) {
      ApkUpdateRefreshListener.scheduleIfAllowed(context);
    }
  }

  public static boolean isPassphraseLockEnabled(@NonNull Context context) {
    return getBooleanPreference(context, PASSPHRASE_LOCK, true);
  }

  public static void setPassphraseLockEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, PASSPHRASE_LOCK, value);
  }

  public static PassphraseLockTriggerPreference getPassphraseLockTrigger(@NonNull Context context) {
    return new PassphraseLockTriggerPreference(getStringSetPreference(context,
            PASSPHRASE_LOCK_TRIGGER,
            new HashSet<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_passphrase_lock_trigger_default)))));
  }

  public static long getPassphraseLockTimeout(@NonNull Context context) {
    return getLongPreference(context, PASSPHRASE_LOCK_TIMEOUT, 0);
  }

  public static boolean isPassphraseLockNotificationsEnabled(@NonNull Context context) {
    return getBooleanPreference(context, PASSPHRASE_LOCK_NOTIFICATIONS, true);
  }

  public static void setPassphraseLockNotificationsEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, PASSPHRASE_LOCK_NOTIFICATIONS, value);
  }

  public static void registerListener(@NonNull Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
    getSharedPreferences(context).registerOnSharedPreferenceChangeListener(listener);
  }

  public static void unregisterListener(@NonNull Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
    getSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
  }

  public static void setHasSeenNetworkConfig(Context context, boolean value) {
    setBooleanPreference(context, NETWORK_CONFIG_SEEN, value);
  }

  public static boolean hasSeenNetworkConfig(Context context) {
    return getBooleanPreference(context, NETWORK_CONFIG_SEEN, true);
  }

  public static ProxyType getProxyType(@NonNull Context context) {
    return ProxyType.fromCode(getStringPreference(context, PROXY_TYPE, null));
  }

  public static String getProxySocksHost(@NonNull Context context) {
    return getStringPreference(context, PROXY_SOCKS_HOST, "localhost");
  }

  public static int getProxySocksPort(@NonNull Context context) {
    return Integer.parseInt(getStringPreference(context, PROXY_SOCKS_PORT, "9050"));
  }

  public static boolean isBiometricScreenLockEnabled(@NonNull Context context) {
    return getBooleanPreference(context, BIOMETRIC_SCREEN_LOCK, false);
  }

  public static void setBiometricScreenLockEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, BIOMETRIC_SCREEN_LOCK, value);
  }

  /**
   * @deprecated Use only during re-reg where user had pinV1.
   */
  @Deprecated
  public static void setV1RegistrationLockEnabled(@NonNull Context context, boolean value) {
    //noinspection deprecation
    setBooleanPreference(context, REGISTRATION_LOCK_PREF_V1, value);
  }

  /**
   * @deprecated Use only for migrations to the Key Backup Store registration pinV2.
   */
  @Deprecated
  public static @Nullable String getDeprecatedV1RegistrationLockPin(@NonNull Context context) {
    //noinspection deprecation
    return getStringPreference(context, REGISTRATION_LOCK_PIN_PREF_V1, null);
  }

  /**
   * @deprecated Use only for migrations to the Key Backup Store registration pinV2.
   */
  @Deprecated
  public static void setV1RegistrationLockPin(@NonNull Context context, String pin) {
    //noinspection deprecation
    setStringPreference(context, REGISTRATION_LOCK_PIN_PREF_V1, pin);
  }

  public static long getRegistrationLockLastReminderTime(@NonNull Context context) {
    return getLongPreference(context, REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS, 0);
  }

  public static void setRegistrationLockLastReminderTime(@NonNull Context context, long time) {
    setLongPreference(context, REGISTRATION_LOCK_LAST_REMINDER_TIME_POST_KBS, time);
  }

  public static long getRegistrationLockNextReminderInterval(@NonNull Context context) {
    return getLongPreference(context, REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL, RegistrationLockReminders.INITIAL_INTERVAL);
  }

  public static void setRegistrationLockNextReminderInterval(@NonNull Context context, long value) {
    setLongPreference(context, REGISTRATION_LOCK_NEXT_REMINDER_INTERVAL, value);
  }

  public static void setBackupPassphrase(@NonNull Context context, @Nullable String passphrase) {
    setStringPreference(context, BACKUP_PASSPHRASE, passphrase);
  }

  public static @Nullable String getBackupPassphrase(@NonNull Context context) {
    return getStringPreference(context, BACKUP_PASSPHRASE, null);
  }

  public static void setEncryptedBackupPassphrase(@NonNull Context context, @Nullable String encryptedPassphrase) {
    setStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase);
  }

  public static @Nullable String getEncryptedBackupPassphrase(@NonNull Context context) {
    return getStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, null);
  }

  @Deprecated
  public static boolean isBackupEnabled(@NonNull Context context) {
    return getBooleanPreference(context, BACKUP_ENABLED, false);
  }

  public static void setNextBackupTime(@NonNull Context context, long time) {
    setLongPreference(context, BACKUP_TIME, time);
  }

  public static long getNextBackupTime(@NonNull Context context) {
    return getLongPreference(context, BACKUP_TIME, -1);
  }

  public static void setBackupInternal(@NonNull Context context, long value) {
    setLongPreference(context, BACKUP_INTERVAL, value);
  }

  public static long getBackupInternal(@NonNull Context context) {
    return getLongPreference(context, BACKUP_INTERVAL, TimeUnit.DAYS.toMillis(1));
  }

  public static void setBackupMaxFiles(@NonNull Context context, int value) {
    setIntegerPrefrence(context, BACKUP_MAX_FILES, value);
  }

  public static int getBackupMaxFiles(@NonNull Context context) {
    return getIntegerPreference(context, BACKUP_MAX_FILES, 2);
  }

  public static boolean getNavbarShowCalls(@NonNull Context context) {
    return true;
  }

  public static void setAttachmentEncryptedSecret(@NonNull Context context, @NonNull String secret) {
    setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret);
  }

  public static void setAttachmentUnencryptedSecret(@NonNull Context context, @Nullable String secret) {
    setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret);
  }

  public static @Nullable String getAttachmentEncryptedSecret(@NonNull Context context) {
    return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null);
  }

  public static @Nullable String getAttachmentUnencryptedSecret(@NonNull Context context) {
    return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null);
  }

  public static void setDatabaseEncryptedSecret(@NonNull Context context, @NonNull String secret) {
    setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret);
  }

  public static void setDatabaseUnencryptedSecret(@NonNull Context context, @Nullable String secret) {
    setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret);
  }

  public static @Nullable String getDatabaseUnencryptedSecret(@NonNull Context context) {
    return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null);
  }

  public static @Nullable String getDatabaseEncryptedSecret(@NonNull Context context) {
    return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null);
  }

  public static void setHasSuccessfullyRetrievedDirectory(Context context, boolean value) {
    setBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, value);
  }

  public static boolean hasSuccessfullyRetrievedDirectory(Context context) {
    return getBooleanPreference(context, SUCCESSFUL_DIRECTORY_PREF, false);
  }

  public static void setUnauthorizedReceived(Context context, boolean value) {
    boolean previous = isUnauthorizedReceived(context);
    setBooleanPreference(context, UNAUTHORIZED_RECEIVED, value);

    if (previous != value) {
      Recipient.self().live().refresh();

      if (value) {
        notifyUnregisteredReceived(context);
        clearLocalCredentials(context);
      }
    }
  }

  public static boolean isUnauthorizedReceived(Context context) {
    return getBooleanPreference(context, UNAUTHORIZED_RECEIVED, false);
  }

  public static boolean isIncognitoKeyboardEnabled(Context context) {
    return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, true);
  }

  public static boolean isReadReceiptsEnabled(Context context) {
    return getBooleanPreference(context, READ_RECEIPTS_PREF, true);
  }

  public static void setReadReceiptsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, READ_RECEIPTS_PREF, enabled);
  }

  public static boolean isTypingIndicatorsEnabled(Context context) {
    return getBooleanPreference(context, TYPING_INDICATORS, true);
  }

  public static void setTypingIndicatorsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, TYPING_INDICATORS, enabled);
  }

  public static boolean isBlockUnknownEnabled(@NonNull Context context) {
    return getBooleanPreference(context, BLOCK_UNKNOWN, false);
  }

  public static void setBlockUnknownEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, BLOCK_UNKNOWN, value);
  }

  /**
   * Only kept so that we can avoid showing the megaphone for the new link previews setting
   * ({@link SettingsValues#isLinkPreviewsEnabled()}) when users upgrade. This can be removed after
   * we stop showing the link previews megaphone.
   */
  public static boolean wereLinkPreviewsEnabled(Context context) {
    return getBooleanPreference(context, LINK_PREVIEWS, true);
  }

  public static int getNotificationPriority(Context context) {
    try {
      return Integer.parseInt(getStringPreference(context, NOTIFICATION_PRIORITY_PREF, String.valueOf(NotificationCompat.PRIORITY_HIGH)));
    } catch (ClassCastException e) {
      return getIntegerPreference(context, NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH);
    }
  }

  /**
   * @deprecated Use {@link SettingsValues#getMessageFontSize()} via {@link org.thoughtcrime.securesms.keyvalue.SignalStore} instead.
   */
  @Deprecated
  public static int getMessageBodyTextSize(Context context) {
    return Integer.parseInt(getStringPreference(context, MESSAGE_BODY_TEXT_SIZE_PREF, "16"));
  }

  public static boolean isTurnOnly(Context context) {
    return getBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, false);
  }

  public static void setDirectCaptureCameraId(Context context, int value) {
    setIntegerPrefrence(context, DIRECT_CAPTURE_CAMERA_ID, value);
  }

  @SuppressWarnings("deprecation")
  public static int getDirectCaptureCameraId(Context context) {
    return getIntegerPreference(context, DIRECT_CAPTURE_CAMERA_ID, CameraInfo.CAMERA_FACING_FRONT);
  }

  @Deprecated
  public static NotificationPrivacyPreference getNotificationPrivacy(Context context) {
    return new NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "none"));
  }

  public static boolean isNewContactsNotificationEnabled(Context context) {
    return getBooleanPreference(context, NEW_CONTACTS_NOTIFICATIONS, false);
  }

  @Deprecated
  public static int getRepeatAlertsCount(Context context) {
    try {
      return Integer.parseInt(getStringPreference(context, REPEAT_ALERTS_PREF, "0"));
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
      return 0;
    }
  }

  @Deprecated
  public static boolean isInThreadNotifications(Context context) {
    return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true);
  }

  public static long getUnidentifiedAccessCertificateRotationTime(Context context) {
    return getLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, 0L);
  }

  public static void setUnidentifiedAccessCertificateRotationTime(Context context, long value) {
    setLongPreference(context, UNIDENTIFIED_ACCESS_CERTIFICATE_ROTATION_TIME_PREF, value);
  }

  public static boolean isUniversalUnidentifiedAccess(Context context) {
    return getBooleanPreference(context, UNIVERSAL_UNIDENTIFIED_ACCESS, false);
  }

  public static void setShowUnidentifiedDeliveryIndicatorsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, enabled);
  }

  public static boolean isShowUnidentifiedDeliveryIndicatorsEnabled(Context context) {
    return getBooleanPreference(context, SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, false);
  }

  public static long getSignedPreKeyRotationTime(Context context) {
    return getLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, 0L);
  }

  public static void setSignedPreKeyRotationTime(Context context, long value) {
    setLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, value);
  }

  public static long getDirectoryRefreshTime(Context context) {
    return getLongPreference(context, DIRECTORY_FRESH_TIME_PREF, 0L);
  }

  public static void setDirectoryRefreshTime(Context context, long value) {
    setLongPreference(context, DIRECTORY_FRESH_TIME_PREF, value);
  }

  public static boolean shouldRefreshFcmToken(Context context) {
    return getBooleanPreference(context, REFRESH_FCM_TOKEN_PREF, false);
  }

  public static void setShouldRefreshFcmToken(Context context, boolean value) {
    setBooleanPreference(context, REFRESH_FCM_TOKEN_PREF, value);
  }

  public static void removeDirectoryRefreshTime(Context context) {
    removePreference(context, DIRECTORY_FRESH_TIME_PREF);
  }

  public static long getUpdateApkRefreshTime(Context context) {
    return getLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, 0L);
  }

  public static void setUpdateApkRefreshTime(Context context, long value) {
    setLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, value);
  }

  public static boolean isUpdateApkEnabled(@NonNull Context context) {
    return getBooleanPreference(context, UPDATE_APK_ENABLED, false);
  }

  public static void setUpdateApkEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, UPDATE_APK_ENABLED, value);
  }

  public static boolean isUpdateApkIncludeBetaEnabled(@NonNull Context context) {
    return getBooleanPreference(context, UPDATE_APK_INCLUDE_BETA, false);
  }

  public static void setUpdateApkIncludeBetaEnabled(@NonNull Context context, boolean value) {
    setBooleanPreference(context, UPDATE_APK_INCLUDE_BETA, value);
  }

  @Deprecated
  public static boolean isEnterSendsEnabled(Context context) {
    return getBooleanPreference(context, ENTER_SENDS_PREF, false);
  }

  public static void setScreenSecurityEnabled(Context context, boolean value) {
    setBooleanPreference(context, SCREEN_SECURITY_PREF, value);
  }

  public static boolean isScreenSecurityEnabled(Context context) {
    return getBooleanPreference(context, SCREEN_SECURITY_PREF, true);
  }

  public static int getLastVersionCodeForMolly(Context context) {
    return getIntegerPreference(context, LAST_VERSION_CODE_PREF, BuildConfig.VERSION_CODE);
  }

  public static void setLastVersionCodeForMolly(Context context, int versionCode) {
    if (!setIntegerPrefrenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
      throw new AssertionError("couldn't write version code to sharedpreferences");
    }
  }

  public static String getTheme(Context context) {
    return getStringPreference(context, THEME_PREF, DynamicTheme.systemThemeAvailable() ? "system" : "light");
  }

  public static void setTheme(Context context, String theme) {
    setStringPreference(context, THEME_PREF, theme);
  }

  public static boolean isDynamicColorsEnabled(Context context) {
    return getBooleanPreference(context, DYNAMIC_COLORS_ENABLED, false);
  }

  public static void setDynamicColorsEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, DYNAMIC_COLORS_ENABLED, enabled);
  }

  public static String getLanguage(Context context) {
    return getStringPreference(context, LANGUAGE_PREF, "zz");
  }

  public static void setLanguage(Context context, String language) {
    setStringPreference(context, LANGUAGE_PREF, language);
  }

  public static boolean hasPromptedPushRegistration(Context context) {
    return getBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, false);
  }

  public static void setPromptedPushRegistration(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_PUSH_REGISTRATION_PREF, value);
  }

  public static void setPromptedOptimizeDoze(Context context, boolean value) {
    setBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, value);
  }

  public static boolean hasPromptedOptimizeDoze(Context context) {
    return getBooleanPreference(context, PROMPTED_OPTIMIZE_DOZE_PREF, false);
  }

  @Deprecated
  public static boolean isNotificationsEnabled(Context context) {
    return getBooleanPreference(context, NOTIFICATION_PREF, true);
  }

  @Deprecated
  public static boolean isCallNotificationsEnabled(Context context) {
    return getBooleanPreference(context, CALL_NOTIFICATIONS_PREF, true);
  }

  @Deprecated
  public static @NonNull Uri getNotificationRingtone(Context context) {
    String result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    return Uri.parse(result);
  }

  @Deprecated
  public static @NonNull Uri getCallNotificationRingtone(Context context) {
    String result = getStringPreference(context, CALL_RINGTONE_PREF, Settings.System.DEFAULT_RINGTONE_URI.toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_RINGTONE_URI.toString();
    }

    return Uri.parse(result);
  }

  @Deprecated
  public static boolean isNotificationVibrateEnabled(Context context) {
    return getBooleanPreference(context, VIBRATE_PREF, true);
  }

  @Deprecated
  public static boolean isCallNotificationVibrateEnabled(Context context) {
    boolean defaultValue = true;

    if (Build.VERSION.SDK_INT >= 23) {
      defaultValue = (Settings.System.getInt(context.getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 1) == 1);
    }

    return getBooleanPreference(context, CALL_VIBRATE_PREF, defaultValue);
  }

  @Deprecated
  public static String getNotificationLedColor(Context context) {
    return getStringPreference(context, LED_COLOR_PREF, "blue");
  }

  @Deprecated
  public static String getNotificationLedPattern(Context context) {
    return getStringPreference(context, LED_BLINK_PREF, "500,2000");
  }

  public static String getNotificationLedPatternCustom(Context context) {
    return getStringPreference(context, LED_BLINK_PREF_CUSTOM, "500,2000");
  }

  public static void setNotificationLedPatternCustom(Context context, String pattern) {
    setStringPreference(context, LED_BLINK_PREF_CUSTOM, pattern);
  }

  public static boolean isSystemEmojiPreferred(Context context) {
    return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false);
  }

  public static void setSystemEmojiPreferred(Context context, boolean useSystemEmoji) {
    setBooleanPreference(context, SYSTEM_EMOJI_PREF, useSystemEmoji);
  }

  public static @NonNull Set<String> getMobileMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default);
  }

  public static @NonNull Set<String> getWifiMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default);
  }

  public static @NonNull Set<String> getRoamingMediaDownloadAllowed(Context context) {
    return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default);
  }

  private static @NonNull Set<String> getMediaDownloadAllowed(Context context, String key, @ArrayRes int defaultValuesRes) {
    return getStringSetPreference(context,
                                  key,
                                  new HashSet<>(Arrays.asList(context.getResources().getStringArray(defaultValuesRes))));
  }

  public static void setLastOutageCheckTime(Context context, long timestamp) {
    setLongPreference(context, LAST_OUTAGE_CHECK_TIME, timestamp);
  }

  public static long getLastOutageCheckTime(Context context) {
    return getLongPreference(context, LAST_OUTAGE_CHECK_TIME, 0);
  }

  public static void setServiceOutage(Context context, boolean isOutage) {
    setBooleanPreference(context, SERVICE_OUTAGE, isOutage);
  }

  public static boolean getServiceOutage(Context context) {
    return getBooleanPreference(context, SERVICE_OUTAGE, false);
  }

  public static long getLastFullContactSyncTime(Context context) {
    return getLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, 0);
  }

  public static void setLastFullContactSyncTime(Context context, long timestamp) {
    setLongPreference(context, LAST_FULL_CONTACT_SYNC_TIME, timestamp);
  }

  public static boolean needsFullContactSync(Context context) {
    return getBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, false);
  }

  public static void setNeedsFullContactSync(Context context, boolean needsSync) {
    setBooleanPreference(context, NEEDS_FULL_CONTACT_SYNC, needsSync);
  }

  public static void setLogEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, LOG_ENABLED, enabled);
  }

  public static boolean isLogEnabled(Context context) {
    return getBooleanPreference(context, LOG_ENABLED, true);
  }

  public static void setLogEncryptedSecret(Context context, String base64Secret) {
    setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret);
  }

  public static String getLogEncryptedSecret(Context context) {
    return getStringPreference(context, LOG_ENCRYPTED_SECRET, null);
  }

  public static void setLogUnencryptedSecret(Context context, String base64Secret) {
    setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret);
  }

  public static String getLogUnencryptedSecret(Context context) {
    return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null);
  }

  public static int getNotificationChannelVersion(Context context) {
    return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1);
  }

  public static void setNotificationChannelVersion(Context context, int version) {
    setIntegerPrefrence(context, NOTIFICATION_CHANNEL_VERSION, version);
  }

  public static int getNotificationMessagesChannelVersion(Context context) {
    return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1);
  }

  public static void setNotificationMessagesChannelVersion(Context context, int version) {
    setIntegerPrefrence(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version);
  }

  public static boolean getNeedsMessagePull(Context context) {
    return getBooleanPreference(context, NEEDS_MESSAGE_PULL, false);
  }

  public static void setNeedsMessagePull(Context context, boolean needsMessagePull) {
    setBooleanPreference(context, NEEDS_MESSAGE_PULL, needsMessagePull);
  }

  public static void setMediaKeyboardMode(Context context, MediaKeyboardMode mode) {
    setStringPreference(context, MEDIA_KEYBOARD_MODE, mode.name());
  }

  public static MediaKeyboardMode getMediaKeyboardMode(Context context) {
    String name = getStringPreference(context, MEDIA_KEYBOARD_MODE, MediaKeyboardMode.EMOJI.name());
    return MediaKeyboardMode.valueOf(name);
  }

  public static void setJobManagerVersion(Context context, int version) {
    setIntegerPrefrence(context, JOB_MANAGER_VERSION, version);
  }

  public static int getJobManagerVersion(Context contex) {
    return getIntegerPreference(contex, JOB_MANAGER_VERSION, 1);
  }

  public static void setAppMigrationVersion(Context context, int version) {
    setIntegerPrefrence(context, APP_MIGRATION_VERSION, version);
  }

  public static int getAppMigrationVersion(Context context) {
    return getIntegerPreference(context, APP_MIGRATION_VERSION, 1);
  }

  public static void setFirstInstallVersion(Context context, int version) {
    setIntegerPrefrence(context, FIRST_INSTALL_VERSION, version);
  }

  public static int getFirstInstallVersion(Context context) {
    return getIntegerPreference(context, FIRST_INSTALL_VERSION, -1);
  }

  public static boolean hasSeenSwipeToReplyTooltip(Context context) {
    return getBooleanPreference(context, HAS_SEEN_SWIPE_TO_REPLY, false);
  }

  public static void setHasSeenSwipeToReplyTooltip(Context context, boolean value) {
    setBooleanPreference(context, HAS_SEEN_SWIPE_TO_REPLY, value);
  }

  public static boolean hasSeenVideoRecordingTooltip(Context context) {
    return getBooleanPreference(context, HAS_SEEN_VIDEO_RECORDING_TOOLTIP, false);
  }

  public static void setHasSeenVideoRecordingTooltip(Context context, boolean value) {
    setBooleanPreference(context, HAS_SEEN_VIDEO_RECORDING_TOOLTIP, value);
  }

  public static void setBooleanPreference(Context context, String key, boolean value) {
    getSharedPreferences(context).edit().putBoolean(key, value).apply();
  }

  public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
    return getSharedPreferences(context).getBoolean(key, defaultValue);
  }

  public static void setStringPreference(Context context, String key, String value) {
    getSharedPreferences(context).edit().putString(key, value).apply();
  }

  public static String getStringPreference(Context context, String key, String defaultValue) {
    return getSharedPreferences(context).getString(key, defaultValue);
  }

  public static int getIntegerPreference(Context context, String key, int defaultValue) {
    return getSharedPreferences(context).getInt(key, defaultValue);
  }

  private static void setIntegerPrefrence(Context context, String key, int value) {
    getSharedPreferences(context).edit().putInt(key, value).apply();
  }

  private static boolean setIntegerPrefrenceBlocking(Context context, String key, int value) {
    return getSharedPreferences(context).edit().putInt(key, value).commit();
  }

  public static long getLongPreference(Context context, String key, long defaultValue) {
    return getSharedPreferences(context).getLong(key, defaultValue);
  }

  private static void setLongPreference(Context context, String key, long value) {
    getSharedPreferences(context).edit().putLong(key, value).apply();
  }

  private static void removePreference(Context context, String key) {
    getSharedPreferences(context).edit().remove(key).apply();
  }

  private static Set<String> getStringSetPreference(Context context, String key, Set<String> defaultValues) {
    final SharedPreferences prefs = getSharedPreferences(context);
    if (prefs.contains(key)) {
      return prefs.getStringSet(key, Collections.<String>emptySet());
    } else {
      return defaultValues;
    }
  }

  private static void clearLocalCredentials(Context context) {
    ProfileKey newProfileKey = ProfileKeyUtil.createNew();
    Recipient  self          = Recipient.self();
    SignalDatabase.recipients().setProfileKey(self.getId(), newProfileKey);

    AppDependencies.getGroupsV2Authorization().clear();
  }

  private static SharedPreferences getSharedPreferences(Context context) {
    return SecurePreferenceManager.getSecurePreferences(context);
  }

  private static void notifyUnregisteredReceived(Context context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "notifyUnregisteredReceived: Notification permission is not granted.");
      return;
    }

    PendingIntent reRegistrationIntent = PendingIntent.getActivity(context,
                                                                   0,
                                                                   RegistrationActivity.newIntentForReRegistration(context),
                                                                   PendingIntent.FLAG_UPDATE_CURRENT | PendingIntentFlags.immutable());
    final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentText(context.getString(R.string.LoggedOutNotification_you_have_been_logged_out))
        .setContentIntent(reRegistrationIntent)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
    NotificationManagerCompat.from(context).notify(NotificationIds.UNREGISTERED_NOTIFICATION_ID, builder.build());
  }

  // NEVER rename these -- they're persisted by name
  public enum MediaKeyboardMode {
    EMOJI, STICKER, GIF
  }
}
