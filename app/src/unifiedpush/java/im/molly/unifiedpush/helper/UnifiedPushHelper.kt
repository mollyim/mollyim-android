package im.molly.unifiedpush.helper

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.unifiedpush.android.connector.UnifiedPush.registerAppWithDialog
import im.molly.unifiedpush.device.MollySocketDevice

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)

  @JvmStatic
  fun initializeUnifiedPush() {
    Log.d(TAG, "##unifiedpush")
    val socketUri = MollySocketDevice().socketUri
    Log.d(TAG, socketUri)
    registerAppWithDialog(ApplicationDependencies.getApplication())
  }
}
