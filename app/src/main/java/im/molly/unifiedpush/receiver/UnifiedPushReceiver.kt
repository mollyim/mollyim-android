package im.molly.unifiedpush.receiver

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.messaging.RemoteMessage
import im.molly.unifiedpush.UnifiedPushNotificationBuilder
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gcm.FcmFetchManager
import org.thoughtcrime.securesms.gcm.FcmReceiveService
import org.thoughtcrime.securesms.jobs.UnifiedPushRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class UnifiedPushReceiver : MessagingReceiver() {

  companion object {
    private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  }

  private val executor = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  private val appLocked
    get() = KeyCachingService.isLocked()

  override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
    Log.i(TAG, "onNewEndpoint($instance)")
    if (!appLocked) {
      refreshEndpoint(endpoint.url)
      if (SignalStore.unifiedpush.airGapped) {
        updateLastReceivedTime(0)
        UnifiedPushNotificationBuilder(context).setNotificationEndpointChangedAirGapped()
      }
    }
  }

  override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
    // called when the registration is not possible, eg. no network
    Log.w(TAG, "onRegistrationFailed($instance)")
    if (!appLocked) {
      // TODO when `reason` is INTERNAL_ERROR, try to register again _one time_
      // TODO when `reason` is ACTION_REQUIRED, tell the distributor requires a user interaction
      // TODO when `reason` is NETWORK, tell to try again when network is back, or implement it.
      UnifiedPushNotificationBuilder(context).setNotificationRegistrationFailed()
    }
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    Log.i(TAG, "onUnregistered($instance)")
    UnifiedPush.forceRemoveDistributor(context)
    if (!appLocked) {
      refreshEndpoint(null)
    }
  }

  override fun onMessage(context: Context, message: PushMessage, instance: String) {
    val msg = message.content.toString(Charsets.UTF_8)

    if (appLocked) {
      onMessageLocked(context, msg)
    } else {
      updateLastReceivedTime(System.currentTimeMillis())
      onMessageUnlocked(context, msg)
    }
  }

  private fun onMessageLocked(context: Context, message: String) {
    when {
      // We look directly in the message to avoid its deserialization
      message.contains("\"urgent\":true") -> {
        if (TextSecurePreferences.isPassphraseLockNotificationsEnabled(context)) {
          Log.d(TAG, "New urgent message received while app is locked.")
          FcmFetchManager.postMayHaveMessagesNotification(context)
        }
      }
    }
  }

  private fun onMessageUnlocked(context: Context, message: String) {
    when {
      message.contains("\"test\":true") -> {
        Log.d(TAG, "Test message received.")
        UnifiedPushNotificationBuilder(context).setNotificationTest()
      }

      else -> {
        if (SignalStore.account.isRegistered && SignalStore.unifiedpush.enabled) {
          Log.d(TAG, "New message")
          executor.enqueue {
            FcmReceiveService.handleReceivedNotification(context, RemoteMessage(bundleOf("google.delivered_priority" to "high")))
          }
        }
      }
    }
  }

  private fun updateLastReceivedTime(timestamp: Long) {
    SignalStore.unifiedpush.lastReceivedTime = timestamp
  }

  private fun refreshEndpoint(endpoint: String?): Boolean {
    val stored = SignalStore.unifiedpush.endpoint
    return if (endpoint != stored) {
      SignalStore.unifiedpush.endpoint = endpoint
      AppDependencies.jobManager.add(UnifiedPushRefreshJob())
      true
    } else false
  }
}
