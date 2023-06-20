package im.molly.unifiedpush.receiver

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.messaging.RemoteMessage
import im.molly.unifiedpush.events.UnifiedPushRegistrationEvent
import im.molly.unifiedpush.model.UnifiedPushStatus
import im.molly.unifiedpush.model.saveStatus
import im.molly.unifiedpush.util.MollySocketRequest
import im.molly.unifiedpush.util.UnifiedPushHelper
import im.molly.unifiedpush.util.UnifiedPushNotificationBuilder
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.gcm.FcmReceiveService
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.unifiedpush.android.connector.MessagingReceiver

class UnifiedPushReceiver : MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val TIMEOUT = 20_000L // 20secs
  private val EXECUTOR = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
    if (SignalStore.unifiedpush().endpoint != endpoint) {
      SignalStore.unifiedpush().endpoint = endpoint
      when (SignalStore.unifiedpush().status) {
        UnifiedPushStatus.AIR_GAPED -> {
          EventBus.getDefault().post(UnifiedPushRegistrationEvent)
          UnifiedPushNotificationBuilder(context).setNotificationEndpointChangedAirGaped()
        }
        UnifiedPushStatus.OK -> {
          EXECUTOR.enqueue {
            MollySocketRequest.registerToMollySocketServer().saveStatus()
            EventBus.getDefault().post(UnifiedPushRegistrationEvent)
            if (SignalStore.unifiedpush().status != UnifiedPushStatus.OK)
              UnifiedPushNotificationBuilder(context).setNotificationEndpointChangedError()
          }
        }
        in listOf(
          UnifiedPushStatus.INTERNAL_ERROR,
          UnifiedPushStatus.MISSING_ENDPOINT,
        ) -> {
          EXECUTOR.enqueue {
            MollySocketRequest.registerToMollySocketServer().saveStatus()
            EventBus.getDefault().post(UnifiedPushRegistrationEvent)
          }
        }
        else -> {
          EventBus.getDefault().post(UnifiedPushRegistrationEvent)
        }
      }
    }
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
    UnifiedPushNotificationBuilder(context).setNotificationRegistrationFailed()
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    SignalStore.unifiedpush().endpoint = null
    EventBus.getDefault().post(UnifiedPushRegistrationEvent)
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    if (UnifiedPushHelper.isUnifiedPushAvailable()) {
      Log.d(TAG, "New message")
      EXECUTOR.enqueue {
        FcmReceiveService.handleReceivedNotification(context, RemoteMessage(bundleOf("google.delivered_priority" to "high")))
      }
    }
  }
}
