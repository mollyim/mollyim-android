package im.molly.unifiedpush

import android.content.Context
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.ui.SelectDistributorDialogsBuilder
import org.unifiedpush.android.connector.ui.UnifiedPushFunctions

object UnifiedPushDistributor {

  @JvmStatic
  fun registerApp() {
    UnifiedPush.registerApp(AppDependencies.application)
  }

  @JvmStatic
  fun unregisterApp() {
    UnifiedPush.unregisterApp(AppDependencies.application)
  }

  fun selectCurrentOrDefaultDistributor() {
    val context = AppDependencies.application
    UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
      if (!success) {
        // If there are multiple distributors installed, but none of them follow the last
        // specifications, we fall back to the first we found.
        UnifiedPush.getDistributors(context).firstOrNull()?.also {
          UnifiedPush.saveDistributor(context, it)
        }
      }
    }
  }

  @JvmStatic
  fun showSelectDistributorDialog(context: Context) {
    SelectDistributorDialogsBuilder(
      context,
      object : UnifiedPushFunctions {
        override fun getAckDistributor(): String? = UnifiedPush.getAckDistributor(context)
        override fun getDistributors(): List<String> = UnifiedPush.getDistributors(context)
        override fun registerApp(instance: String) = UnifiedPush.registerApp(context, instance)
        override fun saveDistributor(distributor: String) = UnifiedPush.saveDistributor(context, distributor)
        override fun tryUseDefaultDistributor(callback: (Boolean) -> Unit) = UnifiedPush.tryUseDefaultDistributor(context, callback)
      }
    ).apply {
      mayUseCurrent = false
      mayUseDefault = false
    }.run()
  }

  fun checkIfActive(): Boolean {
    return UnifiedPush.getAckDistributor(AppDependencies.application) != null
  }
}
