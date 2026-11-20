package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.DeleteAbandonedAttachmentsJob;
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob;
import org.thoughtcrime.securesms.jobs.QuoteThumbnailBackfillJob;
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.migrations.QuoteThumbnailBackfillMigrationJob;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.RecipientIds;
import org.thoughtcrime.securesms.stickers.BlessedPacks;

/**
 * Rule of thumb: if there's something you want to do on the first app launch that involves
 * persisting state to the database, you'll almost certainly *also* want to do it post backup
 * restore, since a backup restore will wipe the current state of the database.
 */
public final class AppInitialization {

  private static final String TAG = Log.tag(AppInitialization.class);

  private AppInitialization() {}

  public static void onFirstEverAppLaunch(@NonNull Context context) {
    Log.i(TAG, "onFirstEverAppLaunch()");

    PlainTextKeyValueStore.setAppMigrationVersion(ApplicationMigrations.CURRENT_VERSION);
    SignalStore.misc().setJobManagerVersion(JobManager.CURRENT_VERSION);
    PlainTextKeyValueStore.setLastVersionCode(BuildConfig.VERSION_CODE);
    AppDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    SignalStore.onFirstEverAppLaunch();
    AppDependencies.getJobManager().addAll(BlessedPacks.getFirstInstallJobs());
  }

  public static void onPostBackupRestore(@NonNull Context context) {
    Log.i(TAG, "onPostBackupRestore()");

    AppDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    SignalStore.onPostBackupRestore();
    SignalStore.onFirstEverAppLaunch();
    SignalStore.onboarding().clearAll();
    SignalStore.notificationProfile().setHasSeenTooltip(true);
    if (NotificationChannels.supported()) {
      NotificationChannels.getInstance().updateMessageVibrate(SignalStore.settings().isMessageVibrateEnabled());
    }
    if (!SignalStore.molly().getLogEnabled()) {
      Log.disableAndWipeLogs();
    }
    if (SignalStore.molly().getUpdateApkEnabled()) {
      ApkUpdateRefreshListener.scheduleIfAllowed(context);
    }
    AppDependencies.getJobManager().addAll(BlessedPacks.getFirstInstallJobs());
    EmojiSearchIndexDownloadJob.scheduleImmediately();
    DeleteAbandonedAttachmentsJob.enqueue();

    RecipientIds.clearCache();
    SignalDatabase.remappedRecords().clearCache();
    SignalDatabase.threads().clearCache();
    AppDependencies.getRecipientCache().clear();
    AppDependencies.getRecipientCache().clearSelf();

    if (SignalStore.misc().startedQuoteThumbnailMigration()) {
      AppDependencies.getJobManager().add(new QuoteThumbnailBackfillJob());
    } else {
      AppDependencies.getJobManager().add(new QuoteThumbnailBackfillMigrationJob());
    }
  }
}
