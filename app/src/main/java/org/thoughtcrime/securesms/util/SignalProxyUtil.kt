package org.thoughtcrime.securesms.util

import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SignalProxyUtil {
  private val TAG = tag(SignalProxyUtil::class.java)

  private const val PROXY_LINK_HOST = "signal.tube"

  /**
   * A blocking call that will wait until the websocket either successfully connects, or fails.
   * It is assumed that the app state is already configured how you would like it, e.g. you've
   * already configured a proxy if relevant.
   *
   * @return True if the connection is successful within the specified timeout, otherwise false.
   */
  @JvmStatic
  @WorkerThread
  fun testWebsocketConnection(timeout: Long): Boolean {
    return testWebsocketConnectionUnregistered(timeout)
  }

  private fun testWebsocketConnectionUnregistered(timeout: Long): Boolean {
    val latch = CountDownLatch(1)
    val success = AtomicBoolean(false)
    val accountManager = AccountManagerFactory.getInstance()
      .createUnauthenticated(AppDependencies.application, "", SignalServiceAddress.DEFAULT_DEVICE_ID, "")

    SignalExecutors.UNBOUNDED.execute {
      try {
        accountManager.checkNetworkConnection()
        success.set(true)
        latch.countDown()
      } catch (_: IOException) {
        latch.countDown()
      }
    }

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
      w(TAG, "Interrupted!", e)
    }

    return success.get()
  }
}
