package im.molly.unifiedpush.util

import im.molly.unifiedpush.device.MollySocketLinkedDevice
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.UnifiedPush.registerAppWithDialog

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = ApplicationDependencies.getApplication()

  @JvmStatic
  fun initializeUnifiedPush() {
    if (isUnifiedPushEnabled()) {
      Log.d(TAG, "Initializing UnifiedPush")
      MollySocketLinkedDevice().device ?: return
      Log.d(TAG, "MollyDevice found")
      registerAppWithDialog(ApplicationDependencies.getApplication())
    }
  }

  @JvmStatic
  fun isUnifiedPushEnabled(): Boolean {
    return SignalStore.account().isRegistered && SignalStore.unifiedpush().enabled
  }

  @JvmStatic
  fun isUnifiedPushAvailable(): Boolean {
    return isUnifiedPushEnabled() &&
      (SignalStore.unifiedpush().airGaped || SignalStore.unifiedpush().mollySocketOk) &&
      UnifiedPush.getDistributor(context).isNotEmpty()
  }

  @JvmStatic
  fun isPushAvailable(): Boolean {
    return SignalStore.account().fcmEnabled || isUnifiedPushAvailable()
  }
}
