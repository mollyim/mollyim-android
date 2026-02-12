package im.molly.unifiedpush

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.unifiedpush.android.connector.UnifiedPush

object UnifiedPushDistributor {

  @JvmStatic
  fun registerApp(vapid: String?) {
    UnifiedPush.register(AppDependencies.application, vapid = vapid)
  }

  @JvmStatic
  fun unregisterApp() {
    UnifiedPush.unregister(AppDependencies.application)
  }

  fun selectFirstDistributor() {
    val context = AppDependencies.application
    UnifiedPush.getDistributors(context).firstOrNull()?.also {
      UnifiedPush.saveDistributor(context, it)
    }
  }

  fun checkIfActive(): Boolean {
    return UnifiedPush.getAckDistributor(AppDependencies.application) != null
  }
}
