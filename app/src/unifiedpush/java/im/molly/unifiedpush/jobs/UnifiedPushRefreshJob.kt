
package im.molly.unifiedpush.jobs

import im.molly.unifiedpush.events.UnifiedPushRegistrationEvent
import im.molly.unifiedpush.model.RegistrationStatus
import im.molly.unifiedpush.model.UnifiedPushStatus
import im.molly.unifiedpush.model.saveStatus
import im.molly.unifiedpush.util.MollySocketRequest
import im.molly.unifiedpush.util.UnifiedPushHelper
import im.molly.unifiedpush.util.UnifiedPushNotificationBuilder
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.util.concurrent.TimeUnit

/*
This job is called when :
- The delivery Method is called
- Delivery method == UnifiedPush and :
  - The app starts
  - one component related to UnifiedPush is changed
 */

class UnifiedPushRefreshJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  constructor() : this(
    Parameters.Builder()
      .setQueue("UnifiedPushRefreshJob")
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(3)
      .setLifespan(TimeUnit.HOURS.toMillis(6))
      .setMaxInstancesForFactory(1)
      .build()
  )

  override fun serialize(): ByteArray? { return null }

  override fun getFactoryKey(): String {
    return KEY
  }

  @Throws(Exception::class)
  public override fun onRun() {
    Log.d(TAG, "Running the refresh job")

    // If this job is called while changing the notification method
    val currentMethod = SignalStore.settings.notificationDeliveryMethod
    if (currentMethod == SettingsValues.NotificationDeliveryMethod.FCM
      || currentMethod == SettingsValues.NotificationDeliveryMethod.WEBSOCKET) {
      Log.d(TAG, "New method: $currentMethod, reinitializing notification services.")
      reInitializeNotificationServices()
      return
    }

    // Else : we try to use UnifiedPush
    UnifiedPushHelper.checkDistributorPresence(context)
    val status = SignalStore.unifiedpush.status
    Log.d(TAG, "UnifiedPush Status: $status")
    when (status) {
      // Should not occur
      UnifiedPushStatus.DISABLED,
      UnifiedPushStatus.UNKNOWN -> Log.e(TAG, "UnifiedPush setup should not be in this state here : $status.")
      // It will fallback on FCM/Websocket
      UnifiedPushStatus.MISSING_ENDPOINT,
      UnifiedPushStatus.NO_DISTRIBUTOR,
      UnifiedPushStatus.LINK_DEVICE_ERROR,
      UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL-> {
        Log.i(TAG, "UnifiedPush enabled, but this is currently unavailable. Status=$status.")
        reInitializeNotificationServices()
      }
      // Considered as successful setup
      UnifiedPushStatus.AIR_GAPED -> {
        Log.i(TAG, "UnifiedPush available in AirGaped mode. No MollySocket to register to.")
        reInitializeNotificationServices()
      }
      // We try to register to MollySocket server,
      // Then re-init the services
      UnifiedPushStatus.PENDING,
      UnifiedPushStatus.FORBIDDEN_UUID,
      UnifiedPushStatus.FORBIDDEN_ENDPOINT,
      UnifiedPushStatus.INTERNAL_ERROR -> {
        Log.i(TAG, "Registering to MollySocket...")
        SignalStore.unifiedpush.pending = false
        val msStatus = MollySocketRequest.registerToMollySocketServer()
        msStatus.saveStatus()
        when (msStatus) {
          RegistrationStatus.INTERNAL_ERROR -> Log.d(TAG, "An error occurred while trying to re-register with MollySocket.")
          RegistrationStatus.OK -> {
            Log.d(TAG, "Successfully re-registered to MollySocket")
            reInitializeNotificationServices()
          }
          else -> Log.d(TAG, "Still not able to register to MollySocket: $msStatus.")
        }
      }
      UnifiedPushStatus.OK -> {
        Log.i(TAG, "Registering again to MollySocket...")
        when (val msStatus = MollySocketRequest.registerToMollySocketServer()) {
          RegistrationStatus.INTERNAL_ERROR -> Log.d(TAG, "An error occurred while trying to re-register with MollySocket. It may be a bad connection: ignore it.")
          RegistrationStatus.OK -> Log.d(TAG, "Successfully re-registered to MollySocket")
          else -> {
            Log.w(TAG, "The registration status has changed!")
            msStatus.saveStatus()
            reInitializeNotificationServices()
            UnifiedPushNotificationBuilder(context).setNotificationMollySocketRegistrationChanged()
          }
        }
      }
    }
    EventBus.getDefault().post(UnifiedPushRegistrationEvent)
  }

  private fun reInitializeNotificationServices() {
    ApplicationContext.getInstance().finalizeMessageRetrieval()
    ApplicationContext.getInstance().initializeFcmCheck()
    ApplicationContext.getInstance().initializeMessageRetrieval()
  }

  override fun onFailure() {
    Log.w(TAG, "MollySocket reregistration failed after retry attempt exhaustion!")
  }

  public override fun onShouldRetry(throwable: Exception): Boolean {
    return throwable !is NonSuccessfulResponseCodeException
  }

  class Factory : Job.Factory<UnifiedPushRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UnifiedPushRefreshJob {
      return UnifiedPushRefreshJob(parameters)
    }
  }

  companion object {
    const val KEY = "UnifiedPushRefreshJob"
    private val TAG = Log.tag(UnifiedPushRefreshJob::class.java)
  }
}
