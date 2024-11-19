package im.molly.unifiedpush.jobs

import im.molly.unifiedpush.UnifiedPushDistributor
import im.molly.unifiedpush.UnifiedPushNotificationBuilder
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PushServiceEvent
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.jobs.FcmRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import im.molly.unifiedpush.MollySocketRepository
import im.molly.unifiedpush.MollySocketRepository.isLinked
import im.molly.unifiedpush.model.RegistrationStatus
import im.molly.unifiedpush.model.toRegistrationStatus
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles UnifiedPush registration and ensures the MollySocket status is up-to-date.
 * Unregisters if the account is not registered or UnifiedPush is disabled.
 */
class UnifiedPushRefreshJob private constructor(
  private val testPing: Boolean,
  parameters: Parameters,
) : BaseJob(parameters) {

  constructor() : this(testPing = false)

  constructor(testPing: Boolean) : this(
    testPing = testPing,
    parameters = Parameters.Builder()
      .setQueue(FcmRefreshJob.QUEUE_KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(3)
      .setLifespan(TimeUnit.HOURS.toMillis(6))
      .setMaxInstancesForFactory(2)
      .build()
  )

  @Throws(Exception::class)
  public override fun onRun() {
    val hasAccount = SignalStore.account.isRegistered
    val enabled = SignalStore.unifiedpush.enabled
    val currentStatus = SignalStore.unifiedpush.registrationStatus

    Log.d(TAG, "Current registration status: $currentStatus")

    if (!hasAccount || !enabled) {
      Log.d(TAG, "UnifiedPush is disabled.")
      return
    }

    try {
      val newStatus = checkRegistrationStatus()

      if (currentStatus == newStatus) {
        Log.d(TAG, "Registration status unchanged.")
      } else {
        Log.d(TAG, "Updated registration status: $newStatus")
        SignalStore.unifiedpush.registrationStatus = newStatus
      }

      when (newStatus) {
        RegistrationStatus.REGISTERED -> UnifiedPushNotificationBuilder(context).clearAlerts()
        RegistrationStatus.FORBIDDEN_ENDPOINT -> UnifiedPushNotificationBuilder(context).setNotificationMollySocketForbiddenEndpoint()
        RegistrationStatus.FORBIDDEN_UUID -> UnifiedPushNotificationBuilder(context).setNotificationMollySocketForbiddenUuid()
        RegistrationStatus.FORBIDDEN_PASSWORD -> UnifiedPushNotificationBuilder(context).setNotificationMollySocketForbiddenPassword()
        else -> {}
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Error checking registration status", t)

      if (currentStatus != RegistrationStatus.REGISTERED) {
        SignalStore.unifiedpush.registrationStatus = RegistrationStatus.UNKNOWN
      }

      // Re-throw the exception as IOException for retry
      when (t) {
        is IOException -> throw t
        else -> throw IOException(t)
      }
    } finally {
      AppDependencies.resetNetwork(restartMessageObserver = true)
      EventBus.getDefault().post(PushServiceEvent)
    }
  }

  @Throws(IOException::class)
  private fun checkRegistrationStatus(): RegistrationStatus {
    val endpoint = SignalStore.unifiedpush.endpoint
    val airGapped = SignalStore.unifiedpush.airGapped
    val mollySocketUrl = SignalStore.unifiedpush.mollySocketUrl?.toHttpUrlOrNull()
    val lastReceivedTime = SignalStore.unifiedpush.lastReceivedTime

    Log.d(TAG, "Last notification received at: $lastReceivedTime")

    SignalStore.unifiedpush.device?.let { device ->
      if (!device.isLinked()) {
        Log.w(TAG, "$device no longer linked, will be recreated.")
        SignalStore.unifiedpush.device = null
      }
    }

    UnifiedPushDistributor.registerApp()

    if (!UnifiedPushDistributor.checkIfActive() || endpoint == null) {
      Log.e(TAG, "Distributor is not active or endpoint is missing.")
      return RegistrationStatus.PENDING
    }

    if (!airGapped && mollySocketUrl == null) {
      Log.e(TAG, "Missing MollySocket URL.")
      return RegistrationStatus.PENDING
    }

    val device = SignalStore.unifiedpush.device ?: try {
      MollySocketRepository.createDevice().also { device ->
        SignalStore.unifiedpush.device = device
        Log.d(TAG, "Created new MollySocket device: $device")
      }
    } catch (e: DeviceLimitExceededException) {
      Log.e(TAG, "Device limit exceeded: ${e.max} total devices already.")
      UnifiedPushNotificationBuilder(context).setNotificationDeviceLimitExceeded(e.max)
      return RegistrationStatus.PENDING
    }

    if (airGapped) {
      return if (lastReceivedTime > 0) {
        Log.d(TAG, "Air-gapped server: Device is registered.")
        RegistrationStatus.REGISTERED
      } else {
        Log.d(TAG, "Air gapped server: Manual registration required!")
        RegistrationStatus.PENDING
      }
    }

    Log.d(TAG, "Re-registering $device on MollySocket server...")

    val result = MollySocketRepository.registerDeviceOnServer(
      url = mollySocketUrl!!,
      device = device,
      endpoint = endpoint,
      ping = testPing,
    )

    Log.d(TAG, "Registration result: $result")

    return result.toRegistrationStatus()
  }

  override fun onFailure() = Unit

  public override fun onShouldRetry(throwable: Exception): Boolean {
    return throwable !is NonSuccessfulResponseCodeException
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBoolean(KEY_PING, testPing)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  class Factory : Job.Factory<UnifiedPushRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UnifiedPushRefreshJob {
      val data = JsonJobData.deserialize(serializedData)
      return UnifiedPushRefreshJob(
        testPing = data.getBoolean(KEY_PING),
        parameters = parameters,
      )
    }
  }

  companion object {
    private val TAG = Log.tag(UnifiedPushRefreshJob::class.java)

    const val KEY = "UnifiedPushRefreshJob"
    private const val KEY_PING = "ping"
  }
}
