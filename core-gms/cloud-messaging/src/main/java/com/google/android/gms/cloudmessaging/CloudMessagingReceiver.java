package com.google.android.gms.cloudmessaging;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.android.gms.common.util.concurrent.NamedThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Keep
public abstract class CloudMessagingReceiver extends BroadcastReceiver {

  private static final String TAG = "CloudMessagingReceiver";

  public static final class IntentActionKeys {
    private static final String NOTIFICATION_OPEN    = "com.google.firebase.messaging.NOTIFICATION_OPEN";
    private static final String NOTIFICATION_DISMISS = "com.google.firebase.messaging.NOTIFICATION_DISMISS";
  }

  public static final class IntentKeys {
    private static final String PENDING_INTENT = "pending_intent";
    private static final String WRAPPED_INTENT = "wrapped_intent";
  }

  private static ThreadPoolExecutor singleThreadExecutor(String threadName) {
    return new ThreadPoolExecutor(
        0, 1, 30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new NamedThreadFactory(threadName)
    );
  }

  private static final class ExecutorsHolder {
    static final ThreadPoolExecutor dispatchExecutor
        = singleThreadExecutor("firebase-iid-executor");

    static final ThreadPoolExecutor ackExecutor
        = singleThreadExecutor("pscm-ack-executor");
  }

  @Override
  public final void onReceive(Context context, Intent intent) {
    if (intent != null) {
      BroadcastReceiver.PendingResult pendingResult = goAsync();
      getBroadcastExecutor().execute(
          () -> handleIntent(intent, context, isOrderedBroadcast(), pendingResult)
      );
    }
  }

  @NonNull
  protected Executor getBroadcastExecutor() {
    return ExecutorsHolder.dispatchExecutor;
  }

  @NonNull
  private Executor getAckExecutor() {
    return ExecutorsHolder.ackExecutor;
  }

  @WorkerThread
  protected abstract int onMessageReceive(@NonNull Context context, @NonNull CloudMessage message);

  @WorkerThread
  protected void onNotificationDismissed(@NonNull Context context, @NonNull Bundle data) {}

  @WorkerThread
  private void handleIntent(@NonNull Intent intent,
                            @NonNull Context context,
                            boolean isOrdered,
                            BroadcastReceiver.PendingResult pendingResult)
  {
    int resultCode = 500;

    Parcelable wrappedIntent = intent.getParcelableExtra(IntentKeys.WRAPPED_INTENT);

    try {
      if (wrappedIntent instanceof Intent) {
        if (BuildConfig.NOTIFICATION_PAYLOAD_ENABLED) {
          resultCode = deliverDismissNotificationAction((Intent) wrappedIntent, context);
        }
      } else {
        if (intent.getExtras() != null) {
          resultCode = deliverCloudMessage(intent, context);
        }
      }
    } finally {
      if (pendingResult != null) {
        if (isOrdered) {
          pendingResult.setResultCode(resultCode);
        }
        pendingResult.finish();
      }
    }
  }

  @WorkerThread
  private int deliverCloudMessage(@NonNull Intent intent, @NonNull Context context) {
    CloudMessage message = new CloudMessage(intent);

    CountDownLatch ackLatch = new CountDownLatch(1);
    getAckExecutor().execute(() -> {
      if (TextUtils.isEmpty(message.getMessageId())) {
        ackLatch.countDown();
        return;
      }

      Bundle data = CloudMessageBundle.forMessage(message);
      data.putBoolean("supports_message_handled", true);

      MessengerIpcClient
          .getInstance(context)
          .sendOneWay(GmsOpCode.BROADCAST_ACK, data)
          .addOnCompleteListener(
              Runnable::run, t -> ackLatch.countDown()
          );
    });

    int result = onMessageReceive(context, message);

    try {
      boolean acked = ackLatch.await(1, TimeUnit.SECONDS);
      if (!acked) {
        Log.w(TAG, "Message ack timed out");
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Message ack failed: " + e);
      Thread.currentThread().interrupt();
    }

    return result;
  }

  @WorkerThread
  private int deliverDismissNotificationAction(@NonNull Intent intent, @NonNull Context context) {
    PendingIntent pendingIntent = intent.getParcelableExtra(IntentKeys.PENDING_INTENT);
    if (pendingIntent != null) {
      try {
        pendingIntent.send();
      } catch (PendingIntent.CanceledException e) {
        Log.e(TAG, "Notification pending intent canceled");
      }
    }

    if (!IntentActionKeys.NOTIFICATION_DISMISS.equals(intent.getAction())) {
      return 500;
    }

    Bundle extras = intent.getExtras();
    if (extras != null) {
      extras.remove(IntentKeys.PENDING_INTENT);
    } else {
      extras = new Bundle();
    }

    onNotificationDismissed(context, extras);
    return -1;
  }
}
