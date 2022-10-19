package im.molly.unifiedpush.helper

import org.signal.core.util.logging.Log
import org.unifiedpush.android.connector.UnifiedPush.registerAppWithDialog
import im.molly.unifiedpush.device.MollySocketDevice
import org.thoughtcrime.securesms.dependencies.AppDependencies

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)

  @JvmStatic
  fun initializeUnifiedPush() {
    Log.d(TAG, "##unifiedpush")
    val socketUri = MollySocketDevice().socketUri
    Log.d(TAG, socketUri)
    registerAppWithDialog(AppDependencies.application)
  }
}
