package im.molly.unifiedpush.util

import im.molly.unifiedpush.device.MollySocketLinkedDevice
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush

object UnifiedPushHelper {
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = AppDependencies.application

  // return false if the initialization failed
  fun initializeMollySocketLinkedDevice(): Boolean {
    if (SignalStore.account.isRegistered) {
      Log.d(TAG, "Initializing UnifiedPush")
      MollySocketLinkedDevice().device ?: run {
        Log.w(TAG, "Can't initialize the linked device for MollySocket")
        return false
      }
      Log.d(TAG, "MollyDevice found")
    } else {
      return false
    }
    return true
  }

  @JvmStatic
  fun isUnifiedPushAvailable(): Boolean {
    return SignalStore.unifiedpush.status in listOf(UnifiedPushStatus.OK, UnifiedPushStatus.AIR_GAPED)
  }

  fun checkDistributorPresence() {
    UnifiedPush.getAckDistributor(context) ?: run { SignalStore.unifiedpush.endpoint = null }
  }
}