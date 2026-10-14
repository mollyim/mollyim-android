package org.thoughtcrime.securesms.contacts;

import org.thoughtcrime.securesms.ScreenLockController;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.BaseActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.List;

/**
 * Activity which displays a dialog to confirm whether to turn off "Contact Joined Signal" notifications.
 */
// Preserve the notification entry flow; screen authentication guards the dialog and its action.
@SuppressLint("BaseActivitySubclass")
public class TurnOffContactJoinedNotificationsActivity extends BaseActivity {
  private androidx.appcompat.app.AlertDialog confirmationDialog;

  private final static String EXTRA_THREAD_ID = "thread_id";

  public static Intent newIntent(@NonNull Context context, long threadId) {
    Intent intent = new Intent(context, TurnOffContactJoinedNotificationsActivity.class);

    intent.putExtra(EXTRA_THREAD_ID, threadId);

    return intent;
  }

  @Override
  protected void onPostResume(boolean screenLocked) {
    super.onPostResume(screenLocked);
    if (screenLocked || !ScreenLockController.isAccessAllowed() || isFinishing() ||
        !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
        (confirmationDialog != null && confirmationDialog.isShowing())) return;

    confirmationDialog = new MaterialAlertDialogBuilder(this)
        .setMessage(R.string.TurnOffContactJoinedNotificationsActivity__turn_off_contact_joined_signal)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          handlePositiveAction(dialog);
        })
        .setNegativeButton(android.R.string.cancel, ((dialog, which) -> {
          dialog.dismiss();
        }))
        .setOnDismissListener(dialog -> finish())
        .show();
  }

  private void handlePositiveAction(@NonNull DialogInterface dialog) {
    final long generation = ScreenLockController.authenticationGeneration();
    SimpleTask.run(getLifecycle(), () -> {
      ScreenLockController.runIfAccessAllowed(generation, () -> {
        ThreadTable threadTable = SignalDatabase.threads();

        List<MessageTable.MarkedMessageInfo> marked = threadTable.setRead(getIntent().getLongExtra(EXTRA_THREAD_ID, -1));
        MarkReadReceiver.process(marked);

        SignalStore.settings().setNotifyWhenContactJoinsSignal(false);
        AppDependencies.getMessageNotifier().updateNotification(this);

      });
      return null;
    }, unused -> {
      dialog.dismiss();
    });
  }
}
