package im.molly.unifiedpush.jobs

import im.molly.unifiedpush.util.UnifiedPushHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.util.concurrent.TimeUnit

class UnifiedPushRefreshJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  constructor() : this(Parameters.Builder()
    .setQueue("UnifiedPushRefreshJob")
    .addConstraint(NetworkConstraint.KEY)
    .setMaxAttempts(3)
    .setLifespan(TimeUnit.HOURS.toMillis(6))
    .setMaxInstancesForFactory(1)
    .build())

  override fun serialize(): Data {
    return Data.EMPTY
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  @Throws(Exception::class)
  public override fun onRun() {
    Log.d(TAG, "Running the refresh job")
    if (!UnifiedPushHelper.isUnifiedPushAvailable() || SignalStore.unifiedpush().airGaped) return
    Log.i(TAG, "Reregistering to MollySocket...")
    //TODO: register to mollysocket
  }

  override fun onFailure() {
    Log.w(TAG, "MollySocket reregistration failed after retry attempt exhaustion!")
  }

  public override fun onShouldRetry(throwable: Exception): Boolean {
    return throwable !is NonSuccessfulResponseCodeException
  }

  class Factory : Job.Factory<UnifiedPushRefreshJob?> {
    override fun create(parameters: Parameters, data: Data): UnifiedPushRefreshJob {
      return UnifiedPushRefreshJob(parameters)
    }
  }

  companion object {
    const val KEY = "UnifiedPushRefreshJob"
    private val TAG = Log.tag(UnifiedPushRefreshJob::class.java)
  }
}
