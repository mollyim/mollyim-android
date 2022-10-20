package im.molly.unifiedpush.receiver

import android.content.Context
import im.molly.unifiedpush.helper.UnifiedPushHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.unifiedpush.android.connector.MessagingReceiver
import java.util.Timer
import kotlin.concurrent.schedule

class UnifiedPushReceiver: MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val TIMEOUT = 20_000L //20secs

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // TODO : start inapp webSocket ? isUnifiedPushEnabled becomes false => The websocket starts
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    if (UnifiedPushHelper.isUnifiedPushEnabled()) {
      Log.d(TAG, "New message")
      /*Thread {
        if (Build.VERSION.SDK_INT >= 31) {
          UnifiedPushFetchManager.enqueue(context, true)
        } else {
          UnifiedPushFetchManager.enqueue(context, false)
        }
      }.start()*/
      ApplicationDependencies.getIncomingMessageObserver().registerKeepAliveToken(UnifiedPushReceiver::class.java.name)
      Timer().schedule(TIMEOUT) {
        ApplicationDependencies.getIncomingMessageObserver().removeKeepAliveToken(UnifiedPushReceiver::class.java.name)
      }
    }
  }
}