package org.thoughtcrime.securesms.components.reminder;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.Release;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

@SuppressLint("BatteryLife")
public class DozeReminder extends Reminder {

  @RequiresApi(api = Build.VERSION_CODES.M)
  public DozeReminder(@NonNull final Context context) {
    super(getDozeTitle(context),
          getDozeText(context));

    setOkListener(v -> {
      TextSecurePreferences.setPromptedOptimizeDoze(context, true);
      Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                 Uri.parse("package:" + context.getPackageName()));
      context.startActivity(intent);
    });

    setDismissListener(v -> TextSecurePreferences.setPromptedOptimizeDoze(context, true));
  }

  public static boolean isEligible(Context context) {
    return !SignalStore.account().isFcmEnabled()                   &&
           !TextSecurePreferences.hasPromptedOptimizeDoze(context) &&
           Build.VERSION.SDK_INT >= 23                             &&
           !((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  private static String getDozeTitle(Context context) {
    if (Release.IS_FOSS) {
      return context.getString(R.string.DozeReminder_optimize_for_timely_notifications);
    } else {
      return context.getString(R.string.DozeReminder_optimize_for_missing_play_services);
    }
  }

  private static String getDozeText(Context context) {
    if (Release.IS_FOSS) {
      return context.getString(R.string.DozeReminder_tap_to_allow_molly_to_retrieve_messages_while_the_device_is_in_standby);
    } else {
      return context.getString(R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery);
    }
  }
}
