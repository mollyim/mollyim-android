package im.molly.unifiedpush.util

import android.content.Context
import im.molly.unifiedpush.device.MollySocketLinkedDevice
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.ChooseDialog
import org.unifiedpush.android.connector.NoDistributorDialog
import org.unifiedpush.android.connector.RegistrationDialogContent
import org.unifiedpush.android.connector.UnifiedPush

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
      val dialogContent = RegistrationDialogContent(
        noDistributorDialog = NoDistributorDialog(
          title = context.getString(R.string.UnifiedPush_RegistrationDialog_NoDistrib_title),
          message = context.getString(R.string.UnifiedPush_RegistrationDialog_NoDistrib_message),
          okButton = context.getString(R.string.UnifiedPush_RegistrationDialog_NoDistrib_ok),
          ignoreButton = context.getString(R.string.UnifiedPush_RegistrationDialog_NoDistrib_ignore)
        ),
        chooseDialog = ChooseDialog(context.getString(R.string.UnifiedPush_RegistrationDialog_Choose_title))
      )
      UnifiedPush.registerAppWithDialog(context, registrationDialogContent = dialogContent)
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