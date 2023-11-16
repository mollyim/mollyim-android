package org.thoughtcrime.securesms.components.reminder;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.PowerManagerCompat;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

@SuppressLint("BatteryLife")
public class DozeReminder extends Reminder {

  public DozeReminder(@NonNull final Context context) {
    super(getDozeTitle(context), getDozeText(context));

    setOkListener(v -> {
      TextSecurePreferences.setPromptedOptimizeDoze(context, true);
      PowerManagerCompat.requestIgnoreBatteryOptimizations(context);
    });

    setDismissListener(v -> TextSecurePreferences.setPromptedOptimizeDoze(context, true));
  }

  public static boolean isEligible(Context context) {
    return !SignalStore.account().isPushAvailable()                   &&
           !TextSecurePreferences.hasPromptedOptimizeDoze(context) &&
           !((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  private static @StringRes int getDozeTitle(Context context) {
    if (BuildConfig.USE_PLAY_SERVICES) {
      return R.string.DozeReminder_optimize_for_missing_play_services;
    } else {
      return R.string.DozeReminder_optimize_for_timely_notifications;
    }
  }

  private static @StringRes int getDozeText(Context context) {
    if (BuildConfig.USE_PLAY_SERVICES) {
      return R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery;
    } else {
      return R.string.DozeReminder_tap_to_allow_molly_to_retrieve_messages_while_the_device_is_in_standby;
    }
  }
}
