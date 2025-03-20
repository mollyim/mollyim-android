package org.thoughtcrime.securesms.util;

import androidx.annotation.StyleRes;

import org.thoughtcrime.securesms.R;

public class DynamicNoActionBarInviteTheme extends DynamicTheme {

  protected @StyleRes int getRegularTheme() {
    return R.style.Signal_DayNight_Invite;
  }

  protected @StyleRes int getDynamicTheme() {
    return getRegularTheme();
  }
}
