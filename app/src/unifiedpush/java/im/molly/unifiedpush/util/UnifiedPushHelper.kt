package im.molly.unifiedpush.util

import im.molly.unifiedpush.device.MollySocketLinkedDevice
import org.signal.core.util.logging.Log
import org.unifiedpush.android.connector.UnifiedPush
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

object UnifiedPushHelper {
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = AppDependencies.application

  @JvmStatic
  fun initializeUnifiedPush() {
    if (isUnifiedPushEnabled()) {
      Log.d(TAG, "Initializing UnifiedPush")
      MollySocketLinkedDevice().device ?: return
      Log.d(TAG, "MollyDevice found")
      UnifiedPush.registerAppWithDialog(AppDependencies.application)
    }
  }

  @JvmStatic
  fun isUnifiedPushEnabled(): Boolean {
    return SignalStore.unifiedpush.enabled
  }

  @JvmStatic
  fun isUnifiedPushAvailable(): Boolean {
    return isUnifiedPushEnabled() &&
      (SignalStore.unifiedpush.airGaped || SignalStore.unifiedpush.mollySocketOk) &&
      UnifiedPush.getDistributor(context).isNotEmpty()
  }
}