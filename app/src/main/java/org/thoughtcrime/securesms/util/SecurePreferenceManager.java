package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.crypto.EncryptedPreferences;

public class SecurePreferenceManager {

  private static final String TAG = Log.tag(SecurePreferenceManager.class);

  public static SharedPreferences getSecurePreferences(Context context) {
    EncryptedPreferences preferences = EncryptedPreferences.create(
            context, getSecurePreferencesName());
    preferences.setEncryptionFilter((key) -> {
      switch (key) {
        case TextSecurePreferences.THEME_PREF:
        case TextSecurePreferences.LANGUAGE_PREF:
        case TextSecurePreferences.PASSPHRASE_LOCK:
        case TextSecurePreferences.FIRST_INSTALL_VERSION:
        case TextSecurePreferences.SYSTEM_EMOJI_PREF:
        case TextSecurePreferences.DIRECTORY_FRESH_TIME_PREF:
        case KeyboardAwareLinearLayout.KEYBOARD_HEIGHT_LANDSCAPE:
        case KeyboardAwareLinearLayout.KEYBOARD_HEIGHT_PORTRAIT:
          return false;
        default:
          return true;
      }
    });
    return preferences;
  }

  public static String getSecurePreferencesName() {
    return BuildConfig.APPLICATION_ID;
  }
}
