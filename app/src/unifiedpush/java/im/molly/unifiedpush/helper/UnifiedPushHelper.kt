package im.molly.unifiedpush.helper

import im.molly.unifiedpush.device.MollySocketDevice
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush.getDistributor
import org.unifiedpush.android.connector.UnifiedPush.registerAppWithDialog

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = ApplicationDependencies.getApplication()

  @JvmStatic
  fun initializeUnifiedPush() {
    if (SignalStore.account().isRegistered) {
      Log.d(TAG, "Initializing UnifiedPush")
      val socketUri = MollySocketDevice().socketUri ?: return
      Log.d(TAG, socketUri)
      registerAppWithDialog(ApplicationDependencies.getApplication())
    }
  }

  @JvmStatic
  fun isUnifiedPushEnabled(): Boolean {
    return SignalStore.account().isRegistered && getDistributor(context).isNotEmpty()
  }

  @JvmStatic
  fun isPushEnabled(): Boolean {
    return isUnifiedPushEnabled() || SignalStore.account().fcmEnabled
  }
}
