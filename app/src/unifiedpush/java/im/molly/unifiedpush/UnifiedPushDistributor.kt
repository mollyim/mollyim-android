package im.molly.unifiedpush

import android.content.Context
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.ui.SelectDistributorDialogBuilder
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

  fun selectFirstDistributor() {
    val context = AppDependencies.application
    UnifiedPush.getSavedDistributor(context)
      ?: UnifiedPush.getDistributors(context).firstOrNull()?.also {
        UnifiedPush.saveDistributor(context, it)
      }
  }

  @JvmStatic
  fun showSelectDistributorDialog(context: Context) {
    SelectDistributorDialogBuilder(
      context,
      listOf(INSTANCE_DEFAULT),
      object : UnifiedPushFunctions {
        override fun getAckDistributor(): String? = UnifiedPush.getAckDistributor(context)
        override fun getDistributors(): List<String> = UnifiedPush.getDistributors(context)
        override fun registerApp(instance: String) = UnifiedPush.registerApp(context, instance)
        override fun saveDistributor(distributor: String) = UnifiedPush.saveDistributor(context, distributor)
      }
    ).show()
  }

  fun checkIfActive(): Boolean {
    return UnifiedPush.getAckDistributor(AppDependencies.application) != null
  }
}
