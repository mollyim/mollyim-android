package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.crypto.EncryptedPreferences;
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;

public class SecurePreferenceManager {

  private static SharedPreferences securePreferences;

  public static SharedPreferences getSecurePreferences(Context context) {
    if (securePreferences == null) {
      securePreferences = createSecurePreferences(context);
    }
    return securePreferences;
  }

  private static SharedPreferences createSecurePreferences(Context context) {
    EncryptedPreferences prefs =
        EncryptedPreferences.create(context, getSecurePreferencesName());
    prefs.setEncryptionFilter((key) -> switch (key) {
      case PlainTextKeyValueStore.THEME,
           PlainTextKeyValueStore.DYNAMIC_COLORS_ENABLED,
           PlainTextKeyValueStore.LANGUAGE,
           PlainTextKeyValueStore.PASSPHRASE_LOCK_ENABLED,
           PlainTextKeyValueStore.PASSPHRASE_LOCK_NOTIFICATIONS_ENABLED,
           PlainTextKeyValueStore.BIOMETRIC_SCREEN_LOCK_ENABLED,
           PlainTextKeyValueStore.SYSTEM_EMOJI_PREFERRED,
           PlainTextKeyValueStore.DIRECTORY_REFRESH_TIME,
           PlainTextKeyValueStore.REFRESH_FCM_TOKEN_REQUIRED,
           PlainTextKeyValueStore.FIRST_INSTALL_VERSION,
           KeyboardAwareLinearLayout.KEYBOARD_HEIGHT_LANDSCAPE,
           KeyboardAwareLinearLayout.KEYBOARD_HEIGHT_PORTRAIT -> false;
      default -> true;
    });
    return prefs;
  }

  public static String getSecurePreferencesName() {
    return BuildConfig.APPLICATION_ID;
  }
}
