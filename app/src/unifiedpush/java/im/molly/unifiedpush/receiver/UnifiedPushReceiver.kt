package im.molly.unifiedpush.receiver

import android.content.Context
import android.os.Build
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.gcm.FcmFetchManager.enqueue
import org.unifiedpush.android.connector.MessagingReceiver

class UnifiedPushReceiver: MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // TODO : start inapp webSocket
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    Log.d(TAG, "New message")
    if (Build.VERSION.SDK_INT >= 31) {
      enqueue(context, true)
    } else {
      enqueue(context, false)
    }
  }
}