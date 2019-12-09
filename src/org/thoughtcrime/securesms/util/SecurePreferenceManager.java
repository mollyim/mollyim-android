package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.thoughtcrime.securesms.crypto.EncryptedPreferences;
import org.thoughtcrime.securesms.logging.Log;

public class SecurePreferenceManager {

  private static final String TAG = Log.tag(SecurePreferenceManager.class);

  public static SharedPreferences getSecurePreferences(Context context) {
    EncryptedPreferences preferences = EncryptedPreferences.create(
            context, getSecurePreferencesName(context));
    preferences.setEncryptionFilter((key) -> {
      switch (key) {
        case TextSecurePreferences.THEME_PREF:
        case TextSecurePreferences.LANGUAGE_PREF:
        case TextSecurePreferences.PASSPHRASE_LOCK:
        case TextSecurePreferences.FIRST_INSTALL_VERSION:
        case TextSecurePreferences.SYSTEM_EMOJI_PREF:
        case "keyboard_height_landscape":
        case "keyboard_height_portrait":
          return false;
        default:
          return true;
      }
    });
    return preferences;
  }

  public static String getSecurePreferencesName(Context context) {
    return context.getPackageName() + "_secure_prefs";
  }
}
