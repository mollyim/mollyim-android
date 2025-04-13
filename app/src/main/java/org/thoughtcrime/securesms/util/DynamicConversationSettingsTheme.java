package org.thoughtcrime.securesms.util;

import androidx.annotation.StyleRes;

import org.thoughtcrime.securesms.R;

public class DynamicConversationSettingsTheme extends DynamicTheme {

  protected @StyleRes int getRegularTheme() {
    return R.style.Signal_DayNight_ConversationSettings;
  }

  protected @StyleRes int getDynamicTheme() {
    return R.style.Theme_Molly_Dynamic_ConversationSettings;
  }
}
