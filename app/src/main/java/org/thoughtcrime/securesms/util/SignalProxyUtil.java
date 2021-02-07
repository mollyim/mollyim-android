package org.thoughtcrime.securesms.util;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.Observer;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.net.PipeConnectivityListener;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SignalProxyUtil {

  private static final String TAG = Log.tag(SignalProxyUtil.class);

  private SignalProxyUtil() {}

  /**
   * A blocking call that will wait until the websocket either successfully connects, or fails.
   * It is assumed that the app state is already configured how you would like it, e.g. you've
   * already configured a proxy if relevant.
   *
   * @return True if the connection is successful within the specified timeout, otherwise false.
   */
  @WorkerThread
  public static boolean testWebsocketConnection(long timeout) {
    if (TextSecurePreferences.getLocalNumber(ApplicationDependencies.getApplication()) == null) {
      Log.i(TAG, "User is unregistered! Doing simple check.");
      return testWebsocketConnectionUnregistered(timeout);
    }

    CountDownLatch latch   = new CountDownLatch(1);
    AtomicBoolean  success = new AtomicBoolean(false);

    Observer<PipeConnectivityListener.State> observer = state -> {
      if (state == PipeConnectivityListener.State.CONNECTED) {
        success.set(true);
        latch.countDown();
      } else if (state == PipeConnectivityListener.State.FAILURE) {
        success.set(false);
        latch.countDown();
      }
    };

    ThreadUtil.runOnMainSync(() -> ApplicationDependencies.getPipeListener().getState().observeForever(observer));

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
    } finally {
      ThreadUtil.runOnMainSync(() -> ApplicationDependencies.getPipeListener().getState().removeObserver(observer));
    }

    return success.get();
  }

  private static boolean testWebsocketConnectionUnregistered(long timeout) {
    CountDownLatch              latch          = new CountDownLatch(1);
    AtomicBoolean               success        = new AtomicBoolean(false);
    SignalServiceAccountManager accountManager = AccountManagerFactory.createUnauthenticated(ApplicationDependencies.getApplication(), "", "");

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        accountManager.checkNetworkConnection();
        success.set(true);
        latch.countDown();
      } catch (IOException e) {
        latch.countDown();
      }
    });

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
    }

    return success.get();
  }
}
