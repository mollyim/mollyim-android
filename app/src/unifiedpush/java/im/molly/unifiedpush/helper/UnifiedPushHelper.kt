package im.molly.unifiedpush.helper

import im.molly.unifiedpush.device.MollySocketDevice
import org.signal.core.util.logging.Log
import org.unifiedpush.android.connector.UnifiedPush.registerAppWithDialog
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush.getDistributor

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = AppDependencies.application

  @JvmStatic
  fun initializeUnifiedPush() {
    if (SignalStore.account.isRegistered) {
      Log.d(TAG, "Initializing UnifiedPush")
      val socketUri = MollySocketDevice().socketUri ?: return
      Log.d(TAG, socketUri)
      registerAppWithDialog(AppDependencies.application)
    }
  }

  @JvmStatic
  fun isUnifiedPushEnabled(): Boolean {
    return getDistributor(context).isNotEmpty()
  }
}