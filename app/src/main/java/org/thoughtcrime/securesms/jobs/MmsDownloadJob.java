package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.whispersystems.libsignal.util.guava.Optional;

public class MmsDownloadJob extends BaseJob {

  public static final String KEY = "MmsDownloadJob";

  private static final String TAG = Log.tag(MmsDownloadJob.class);

  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_THREAD_ID  = "thread_id";
  private static final String KEY_AUTOMATIC  = "automatic";

  private long    messageId;
  private long    threadId;
  private boolean automatic;

  public MmsDownloadJob(long messageId, long threadId, boolean automatic) {
    this(new Job.Parameters.Builder()
                           .setQueue("mms-operation")
                           .setMaxAttempts(25)
                           .build(),
         messageId,
         threadId,
         automatic);

  }

  private MmsDownloadJob(@NonNull Job.Parameters parameters, long messageId, long threadId, boolean automatic) {
    super(parameters);

    this.messageId = messageId;
    this.threadId  = threadId;
    this.automatic = automatic;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putLong(KEY_THREAD_ID, threadId)
                             .putBoolean(KEY_AUTOMATIC, automatic)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    if (automatic && KeyCachingService.isLocked(context)) {
      SignalDatabase.mms().markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
    }
  }

  @Override
  public void onRun() {
    if (SignalStore.account().getE164() == null) {
      throw new NotReadyException();
    }

    MessageDatabase                           database     = SignalDatabase.mms();
    Optional<MmsDatabase.MmsNotificationInfo> notification = database.getNotification(messageId);

    if (!notification.isPresent()) {
      Log.w(TAG, "No notification for ID: " + messageId);
      return;
    }

    try {
      if (notification.get().getContentLocation() == null) {
        throw new MmsException("Notification content location was null.");
      }

      if (!SignalStore.account().isRegistered()) {
        throw new MmsException("Not registered");
      }

      database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_CONNECTING);

      throw new MmsException("Download disabled");

    } catch (MmsException e) {
      Log.w(TAG, e);
      handleDownloadError(messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_HARD_FAILURE,
                          automatic);
    }
  }

  @Override
  public void onFailure() {
    MessageDatabase database = SignalDatabase.mms();
    database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE);

    if (automatic) {
      database.markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  private void handleDownloadError(long messageId, long threadId, int downloadStatus, boolean automatic)
  {
    MessageDatabase db = SignalDatabase.mms();

    db.markDownloadState(messageId, downloadStatus);

    if (automatic) {
      db.markIncomingNotificationReceived(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
    }
  }

  public static final class Factory implements Job.Factory<MmsDownloadJob> {
    @Override
    public @NonNull MmsDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MmsDownloadJob(parameters,
                                data.getLong(KEY_MESSAGE_ID),
                                data.getLong(KEY_THREAD_ID),
                                data.getBoolean(KEY_AUTOMATIC));
    }
  }

  private static class NotReadyException extends RuntimeException {
  }
}
