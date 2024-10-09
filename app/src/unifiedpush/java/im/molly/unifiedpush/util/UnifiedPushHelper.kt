package im.molly.unifiedpush.util

import android.content.Context
import im.molly.unifiedpush.device.MollySocketLinkedDevice
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.ui.SelectDistributorDialogBuilder
import org.unifiedpush.android.connector.ui.UnifiedPushFunctions

object UnifiedPushHelper {
  private val TAG = Log.tag(UnifiedPushHelper::class.java)

  // return false if the initialization failed
  fun initializeMollySocketLinkedDevice(context: Context): Boolean {
    if (SignalStore.account.isRegistered) {
      Log.d(TAG, "Initializing UnifiedPush")
      MollySocketLinkedDevice(context).device ?: run {
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
  fun registerAppWithDialogIfNeeded(context: Context) {
    checkDistributorPresence(context)
    if (SignalStore.unifiedpush.status == UnifiedPushStatus.MISSING_ENDPOINT) {
      object : SelectDistributorDialogBuilder(
        context,
        listOf(INSTANCE_DEFAULT),
        object : UnifiedPushFunctions {
          override fun getAckDistributor(): String? = UnifiedPush.getAckDistributor(context)
          override fun getDistributors(): List<String> = UnifiedPush.getDistributors(context)
          override fun registerApp(instance: String) = UnifiedPush.registerApp(context, instance)
          override fun saveDistributor(distributor: String) = UnifiedPush.saveDistributor(context, distributor)
        },
      ){}.show()
    }
  }

  @JvmStatic
  fun isUnifiedPushAvailable(): Boolean {
    return SignalStore.unifiedpush.status in listOf(UnifiedPushStatus.OK, UnifiedPushStatus.AIR_GAPED)
  }

  fun checkDistributorPresence(context: Context) {
    UnifiedPush.getAckDistributor(context) ?: run { SignalStore.unifiedpush.endpoint = null }
  }
}