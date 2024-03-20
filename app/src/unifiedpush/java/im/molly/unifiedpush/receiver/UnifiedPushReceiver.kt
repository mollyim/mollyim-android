package im.molly.unifiedpush.receiver

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.messaging.RemoteMessage
import im.molly.unifiedpush.jobs.UnifiedPushRefreshJob
import im.molly.unifiedpush.util.UnifiedPushHelper
import im.molly.unifiedpush.util.UnifiedPushNotificationBuilder
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gcm.FcmFetchManager
import org.thoughtcrime.securesms.gcm.FcmReceiveService
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.unifiedpush.android.connector.MessagingReceiver

class UnifiedPushReceiver : MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val EXECUTOR = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint !")
    if (SignalStore.unifiedpush.endpoint != endpoint) {
      SignalStore.unifiedpush.endpoint = endpoint
      AppDependencies.jobManager.add(UnifiedPushRefreshJob())
    }
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
    UnifiedPushNotificationBuilder(context).setNotificationRegistrationFailed()
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    SignalStore.unifiedpush.endpoint = null
    AppDependencies.jobManager.add(UnifiedPushRefreshJob())
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    val msg = message.toString(Charsets.UTF_8)
    if (msg.contains("\"test\":true")) {
      Log.d(TAG, "Test message received.")
      UnifiedPushNotificationBuilder(context).setNotificationTest()
      return
    }
    if (KeyCachingService.isLocked()) {
      // We look directly in the message to avoid its deserialization
      if (msg.contains("\"urgent\":true") &&
        TextSecurePreferences.isPassphraseLockNotificationsEnabled(context)) {
        Log.d(TAG, "New urgent message received while app is locked.")
        FcmFetchManager.postMayHaveMessagesNotification(context)
      }
      return
    }

    if (SignalStore.account.isRegistered && UnifiedPushHelper.isUnifiedPushAvailable()) {
      Log.d(TAG, "New message")
      EXECUTOR.enqueue {
        FcmReceiveService.handleReceivedNotification(context, RemoteMessage(bundleOf("google.delivered_priority" to "high")))
      }
    }
  }
}
