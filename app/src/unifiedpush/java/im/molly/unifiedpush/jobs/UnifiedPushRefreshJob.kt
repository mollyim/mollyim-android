package im.molly.unifiedpush.jobs

import im.molly.unifiedpush.model.RegistrationStatus
import im.molly.unifiedpush.model.saveStatus
import im.molly.unifiedpush.util.MollySocketRequest
import im.molly.unifiedpush.util.UnifiedPushHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.util.concurrent.TimeUnit

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
    UnifiedPushHelper.checkDistributorPresence()
    if (UnifiedPushHelper.isUnifiedPushAvailable() && !SignalStore.unifiedpush().airGaped) {
      Log.i(TAG, "Reregistering to MollySocket...")
      when (val status = MollySocketRequest.registerToMollySocketServer()) {
        RegistrationStatus.INTERNAL_ERROR -> Log.d(TAG, "An error occurred while trying to re-register with MollySocket. It may be a bad connection: ignore it.")
        RegistrationStatus.OK -> Log.d(TAG, "Successfully re-registered to MollySocket")
        else -> {
          Log.w(TAG, "The registration status has changed!")
          status.saveStatus()
          ApplicationContext.getInstance().initializeFcmCheck()
          // TODO: alert user
        }
      }
    } else {
      ApplicationContext.getInstance().initializeFcmCheck()
      // TODO: alert user
    }
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
