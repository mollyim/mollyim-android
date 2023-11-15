package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.crypto.EncryptedPreferences;

public class SecurePreferenceManager {

  private static final String TAG = Log.tag(SecurePreferenceManager.class);

  private static SharedPreferences securePreferences;

  public static SharedPreferences getSecurePreferences(Context context) {
    if (securePreferences == null) {
      securePreferences = createSecurePreferences(context);
    }
    return securePreferences;
  }

  private static SharedPreferences createSecurePreferences(Context context) {
    EncryptedPreferences prefs = EncryptedPreferences.create(
            context, getSecurePreferencesName());
    prefs.setEncryptionFilter((key) -> {
      switch (key) {
        case TextSecurePreferences.THEME_PREF:
        case TextSecurePreferences.LANGUAGE_PREF:
        case TextSecurePreferences.PASSPHRASE_LOCK:
        case TextSecurePreferences.PASSPHRASE_LOCK_NOTIFICATIONS:
        case TextSecurePreferences.BIOMETRIC_SCREEN_LOCK:
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
    return prefs;
  }

  public static String getSecurePreferencesName() {
    return BuildConfig.APPLICATION_ID;
  }
}
