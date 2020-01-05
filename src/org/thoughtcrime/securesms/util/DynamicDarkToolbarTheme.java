package org.thoughtcrime.securesms.util;

import android.app.Activity;

import org.thoughtcrime.securesms.R;

public class DynamicDarkToolbarTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("dark")) {
      return R.style.TextSecure_DarkNoActionBar_DarkToolbar;
    }
    else if (theme.equals("oled")) {
      return R.style.TextSecure_DarkNoActionBar_DarkToolbar;
    }

    return R.style.TextSecure_LightNoActionBar_DarkToolbar;
  }
}
