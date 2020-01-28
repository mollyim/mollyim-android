package org.thoughtcrime.securesms;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.viewpager.widget.ViewPager;

import com.melnykov.fab.FloatingActionButton;

import org.thoughtcrime.securesms.IntroPagerAdapter.IntroPage;
import org.thoughtcrime.securesms.experienceupgrades.StickersIntroFragment;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;

public class ExperienceUpgradeActivity extends BaseActionBarActivity
                                       implements StickersIntroFragment.Controller
{
  private static final String TAG             = ExperienceUpgradeActivity.class.getSimpleName();
  private static final String DISMISS_ACTION  = "org.thoughtcrime.securesms.ExperienceUpgradeActivity.DISMISS_ACTION";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private enum ExperienceUpgrade {
    STICKERS(580,
             new IntroPage(0xFF2090EA, StickersIntroFragment.newInstance()),
             R.string.ExperienceUpgradeActivity_introducing_stickers,
             R.string.ExperienceUpgradeActivity_why_use_words_when_you_can_use_stickers,
             R.string.ExperienceUpgradeActivity_why_use_words_when_you_can_use_stickers,
             null,
             true);

    private            int             version;
    private            List<IntroPage> pages;
    private @StringRes int             notificationTitle;
    private @StringRes int             notificationText;
    private @StringRes int             notificationBigText;
    private @Nullable  Class           nextIntent;
    private            boolean         handlesNavigation;

    ExperienceUpgrade(int version,
                      @NonNull List<IntroPage> pages,
                      @StringRes int notificationTitle,
                      @StringRes int notificationText,
                      @StringRes int notificationBigText,
                      @Nullable  Class nextIntent,
                      boolean handlesNavigation)
    {
      this.version             = version;
      this.pages               = pages;
      this.notificationTitle   = notificationTitle;
      this.notificationText    = notificationText;
      this.notificationBigText = notificationBigText;
      this.nextIntent          = nextIntent;
      this.handlesNavigation = handlesNavigation;
    }

    ExperienceUpgrade(int version,
                      @NonNull IntroPage page,
                      @StringRes int notificationTitle,
                      @StringRes int notificationText,
                      @StringRes int notificationBigText,
                      @Nullable Class nextIntent,
                      boolean handlesNavigation)
    {
      this(version, Collections.singletonList(page), notificationTitle, notificationText, notificationBigText, nextIntent, handlesNavigation);
    }

    public int getVersion() {
      return version;
    }

    public List<IntroPage> getPages() {
      return pages;
    }

    public IntroPage getPage(int i) {
      return pages.get(i);
    }

    public int getNotificationTitle() {
      return notificationTitle;
    }

    public int getNotificationText() {
      return notificationText;
    }

    public int getNotificationBigText() {
      return notificationBigText;
    }

    public boolean handlesNavigation() {
      return handlesNavigation;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dynamicTheme.onCreate(this);

    final Optional<ExperienceUpgrade> upgrade = getExperienceUpgrade(this);
    if (!upgrade.isPresent()) {
      onContinue(upgrade);
      return;
    }

    setContentView(R.layout.experience_upgrade_activity);
    final ViewPager            pager = ViewUtil.findById(this, R.id.pager);
    final FloatingActionButton fab   = ViewUtil.findById(this, R.id.fab);

    pager.setAdapter(new IntroPagerAdapter(getSupportFragmentManager(), upgrade.get().getPages()));

    if (upgrade.get().handlesNavigation()) {
      fab.setVisibility(View.GONE);
    } else {
      fab.setVisibility(View.VISIBLE);
      fab.setOnClickListener(v -> onContinue(upgrade));
    }

    getWindow().setBackgroundDrawable(new ColorDrawable(upgrade.get().getPage(0).backgroundColor));
    ServiceUtil.getNotificationManager(this).cancel(NotificationIds.EXPERIENCE_UPGRADE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void onContinue(Optional<ExperienceUpgrade> seenUpgrade) {
    ServiceUtil.getNotificationManager(this).cancel(NotificationIds.EXPERIENCE_UPGRADE);
    int latestVersion = seenUpgrade.isPresent() ? seenUpgrade.get().getVersion()
                                                : Util.getCanonicalVersionCode();
    TextSecurePreferences.setLastExperienceVersionCode(this, latestVersion);
    if (seenUpgrade.isPresent() && seenUpgrade.get().nextIntent != null) {
      Intent intent     = new Intent(this, seenUpgrade.get().nextIntent);
      // TODO [greyson] Navigation
      Intent nextIntent = new Intent(this, MainActivity.class);
      intent.putExtra("next_intent", nextIntent);
      startActivity(intent);
    } else {
      startActivity(getIntent().getParcelableExtra("next_intent"));
    }

    finish();
  }

  public static boolean isUpdate(Context context) {
    return getExperienceUpgrade(context).isPresent();
  }

  public static Optional<ExperienceUpgrade> getExperienceUpgrade(Context context) {
    final int currentVersionCode = Util.getCanonicalVersionCode();
    final int lastSeenVersion    = TextSecurePreferences.getLastExperienceVersionCode(context);
    Log.i(TAG, "getExperienceUpgrade(" + lastSeenVersion + ")");

    if (lastSeenVersion >= currentVersionCode) {
      TextSecurePreferences.setLastExperienceVersionCode(context, currentVersionCode);
      return Optional.absent();
    }

    Optional<ExperienceUpgrade> eligibleUpgrade = Optional.absent();
    for (ExperienceUpgrade upgrade : ExperienceUpgrade.values()) {
      if (lastSeenVersion < upgrade.getVersion()) eligibleUpgrade = Optional.of(upgrade);
    }

    return eligibleUpgrade;
  }

  @Override
  public void onStickersFinished() {
    onContinue(Optional.of(ExperienceUpgrade.STICKERS));
  }

  public static class AppUpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) &&
          intent.getData().getSchemeSpecificPart().equals(context.getPackageName()))
      {
        if (TextSecurePreferences.isPassphraseLockEnabled(context)) {
          Notification notification = new NotificationCompat.Builder(context, NotificationChannels.OTHER)
              .setSmallIcon(R.drawable.icon_notification)
              .setColor(context.getResources().getColor(R.color.signal_primary))
              .setContentTitle(context.getString(R.string.ExperienceUpgradeActivity_unlock_to_complete_update))
              .setContentText(context.getString(R.string.ExperienceUpgradeActivity_please_unlock_signal_to_complete_update))
              .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.ExperienceUpgradeActivity_please_unlock_signal_to_complete_update)))
              .setAutoCancel(true)
              .setContentIntent(PendingIntent.getActivity(context, 0,
                                                          context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()),
                                                          PendingIntent.FLAG_UPDATE_CURRENT))
              .build();

          ServiceUtil.getNotificationManager(context).notify(NotificationIds.EXPERIENCE_UPGRADE, notification);
        }

        Optional<ExperienceUpgrade> experienceUpgrade = getExperienceUpgrade(context);

        if (!experienceUpgrade.isPresent()) {
          return;
        }

        if (experienceUpgrade.get().getVersion() == TextSecurePreferences.getExperienceDismissedVersionCode(context)) {
          return;
        }

        Intent targetIntent  = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        Intent dismissIntent = new Intent(context, AppUpgradeReceiver.class);
        dismissIntent.setAction(DISMISS_ACTION);

        Notification notification = new NotificationCompat.Builder(context, NotificationChannels.OTHER)
                                        .setSmallIcon(R.drawable.icon_notification)
                                        .setColor(context.getResources().getColor(R.color.signal_primary))
                                        .setContentTitle(context.getString(experienceUpgrade.get().getNotificationTitle()))
                                        .setContentText(context.getString(experienceUpgrade.get().getNotificationText()))
                                        .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(experienceUpgrade.get().getNotificationBigText())))
                                        .setAutoCancel(true)
                                        .setContentIntent(PendingIntent.getActivity(context, 0,
                                                                                    targetIntent,
                                                                                    PendingIntent.FLAG_UPDATE_CURRENT))

                                        .setDeleteIntent(PendingIntent.getBroadcast(context, 0,
                                                                                    dismissIntent,
                                                                                    PendingIntent.FLAG_UPDATE_CURRENT))
                                        .build();
        ServiceUtil.getNotificationManager(context).notify(NotificationIds.EXPERIENCE_UPGRADE, notification);
      } else if (DISMISS_ACTION.equals(intent.getAction())) {
        TextSecurePreferences.setExperienceDismissedVersionCode(context, Util.getCanonicalVersionCode());
      }
    }
  }
}
