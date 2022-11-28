package im.molly.unifiedpush.receiver

import android.content.Context
import im.molly.unifiedpush.model.UnifiedPushStatus
import im.molly.unifiedpush.model.saveStatus
import im.molly.unifiedpush.util.MollySocketRequest
import im.molly.unifiedpush.util.UnifiedPushHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.MessagingReceiver
import java.util.Timer
import kotlin.concurrent.schedule

class UnifiedPushReceiver: MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val TIMEOUT = 20_000L //20secs

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
    if (SignalStore.unifiedpush().endpoint != endpoint) {
      SignalStore.unifiedpush().endpoint = endpoint
      when (SignalStore.unifiedpush().status) {
        UnifiedPushStatus.AIR_GAPED -> {
          //TODO: alert if air gaped and endpoint changes
        }
        in listOf(
          UnifiedPushStatus.INTERNAL_ERROR,
          UnifiedPushStatus.MISSING_ENDPOINT,
          UnifiedPushStatus.OK,
        ) -> {
          Thread {
            MollySocketRequest.registerToMollySocketServer().saveStatus()
            //TODO: alert if status changes from Ok to something else
          }.start()
        }
        else -> {}
      }
    }
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
    //TODO: alert user the registration has failed
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    SignalStore.unifiedpush().endpoint = null
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    if (UnifiedPushHelper.isUnifiedPushAvailable()) {
      Log.d(TAG, "New message")
      ApplicationDependencies.getIncomingMessageObserver().registerKeepAliveToken(UnifiedPushReceiver::class.java.name)
      Timer().schedule(TIMEOUT) {
        ApplicationDependencies.getIncomingMessageObserver().removeKeepAliveToken(UnifiedPushReceiver::class.java.name)
      }
    }
  }
}