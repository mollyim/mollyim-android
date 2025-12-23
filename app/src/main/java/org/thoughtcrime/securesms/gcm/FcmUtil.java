package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.installations.FirebaseInstallationsException;
import com.google.firebase.messaging.FirebaseMessaging;

import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class FcmUtil {

  private static final String TAG = Log.tag(FcmUtil.class);

  /**
   * Retrieves the current FCM token. If one isn't available, it'll be generated.
   */
  @WorkerThread
  public static Optional<String> getToken(Context context) {
    if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
      return getTokenWithoutAvailCheck(context);
    }
    return Optional.empty();
  }

  @WorkerThread
  public static Optional<String> getTokenWithoutAvailCheck(Context context) {
    String token = null;

    FirebaseApp.initializeApp(context);

    try {
      token = Tasks.await(FirebaseMessaging.getInstance().getToken());
    } catch (InterruptedException e) {
      Log.w(TAG, "Was interrupted while waiting for the token.");
    } catch (ExecutionException e) {
      Log.w(TAG, "Failed to get the token.", e.getCause());
    }

    return Optional.ofNullable(TextUtils.isEmpty(token) ? null : token);
  }

  @WorkerThread
  public static void deleteFirebaseInstallationId(Context context) throws IOException {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(context);

    if (firebaseApp != null) {
      try {
        Tasks.await(FirebaseInstallations.getInstance(firebaseApp).delete());
      } catch (InterruptedException e) {
        Log.w(TAG, "FID deletion interrupted");
      } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        Log.w(TAG, "Failed to delete FID", cause);
        if (cause instanceof FirebaseInstallationsException) {
          throw new IOException(cause);
        }
      }
    }
  }
}
