package org.thoughtcrime.securesms.migrations;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import org.signal.core.util.SetUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.List;
import java.util.Set;

/**
 * Show a user that contacts are newly available. Only for users that recently installed.
 */
public class UserNotificationMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(UserNotificationMigrationJob.class);

  public static final String KEY =  "UserNotificationMigration";

  UserNotificationMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private UserNotificationMigrationJob(Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    if (!SignalStore.account().isRegistered()   ||
        SignalStore.account().getE164() == null ||
        SignalStore.account().getAci() == null)
    {
      Log.w(TAG, "Not registered! Skipping.");
      return;
    }

    if (!SignalStore.settings().isNotifyWhenContactJoinsSignal()) {
      Log.w(TAG, "New contact notifications disabled! Skipping.");
      return;
    }

    if (TextSecurePreferences.getFirstInstallVersion(context) < 759) {
      Log.w(TAG, "Install is older than v5.0.8. Skipping.");
      return;
    }

    ThreadTable threadTable = SignalDatabase.threads();

    int threadCount = threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, null) +
                      threadTable.getArchivedConversationListCount(ConversationFilter.OFF);

    if (threadCount >= 3) {
      Log.w(TAG, "Already have 3 or more threads. Skipping.");
      return;
    }

    Set<RecipientId>  registered               = SignalDatabase.recipients().getRegistered();
    List<RecipientId> systemContacts           = SignalDatabase.recipients().getSystemContacts();
    Set<RecipientId>  registeredSystemContacts = SetUtil.intersection(registered, systemContacts);
    Set<RecipientId>  threadRecipients         = threadTable.getAllThreadRecipients();

    if (threadRecipients.containsAll(registeredSystemContacts)) {
      Log.w(TAG, "Threads already exist for all relevant contacts. Skipping.");
      return;
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Notification permission is not granted. Skipping.");
      return;
    }

    String message = context.getResources().getQuantityString(R.plurals.UserNotificationMigrationJob_d_contacts_are_on_signal,
                                                              registeredSystemContacts.size(),
                                                              registeredSystemContacts.size());

    Intent        mainActivityIntent    = new Intent(context, MainActivity.class);
    Intent        newConversationIntent = new Intent(context, NewConversationActivity.class);
    PendingIntent pendingIntent         = TaskStackBuilder.create(context)
                                                          .addNextIntent(mainActivityIntent)
                                                          .addNextIntent(newConversationIntent)
                                                          .getPendingIntent(0, 0);

    Notification notification = new NotificationCompat.Builder(context, NotificationChannels.getInstance().getMessagesChannel())
                                                      .setSmallIcon(R.drawable.ic_notification)
                                                      .setContentText(message)
                                                      .setContentIntent(pendingIntent)
                                                      .build();

    try {
      NotificationManagerCompat.from(context)
                               .notify(NotificationIds.USER_NOTIFICATION_MIGRATION, notification);
    } catch (Throwable t) {
      Log.w(TAG, "Failed to notify!", t);
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<UserNotificationMigrationJob> {

    @Override
    public @NonNull UserNotificationMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new UserNotificationMigrationJob(parameters);
    }
  }
}
