package org.thoughtcrime.securesms.util;

import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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
    return testWebsocketConnectionUnregistered(timeout);
  }

  private static boolean testWebsocketConnectionUnregistered(long timeout) {
    CountDownLatch              latch          = new CountDownLatch(1);
    AtomicBoolean               success        = new AtomicBoolean(false);
    SignalServiceAccountManager accountManager = AccountManagerFactory.getInstance().createUnauthenticated(AppDependencies.getApplication(), "", SignalServiceAddress.DEFAULT_DEVICE_ID, "");

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
