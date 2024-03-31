package im.molly.unifiedpush.components.settings.app.notifications

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import im.molly.unifiedpush.events.UnifiedPushRegistrationEvent
import im.molly.unifiedpush.jobs.UnifiedPushRefreshJob
import im.molly.unifiedpush.model.UnifiedPushStatus
import im.molly.unifiedpush.util.MollySocketRequest
import org.greenrobot.eventbus.Subscribe
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.thoughtcrime.securesms.util.livedata.Store
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPushSettingsViewModel(private val application: Application) : ViewModel() {

  private val TAG = Log.tag(UnifiedPushSettingsViewModel::class.java)
  private val store = Store(getState())
  private var status: UnifiedPushStatus? = null
  private val EXECUTOR = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  val state: LiveData<UnifiedPushSettingsState> = store.stateLiveData

  @Subscribe
  fun onStatusRefreshed(e: UnifiedPushRegistrationEvent) {
    Log.d(TAG, "Received event to refresh.")
    status = SignalStore.unifiedpush.status
    store.update { getState() }
  }

  private fun getState(): UnifiedPushSettingsState {
    status ?: run { status = SignalStore.unifiedpush.status }
    val distributor = UnifiedPush.getAckDistributor(application)
    var count = -1
    var selected = -1

    var distributors = UnifiedPush.getDistributors(application).map {
      count++
      if (it == distributor) {
        selected = count
      }
      Distributor(
        applicationId = it,
        name = try {
          val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.getApplicationInfo(it,
              PackageManager.ApplicationInfoFlags.of(
                PackageManager.GET_META_DATA.toLong()
              )
            )
          } else {
            application.packageManager.getApplicationInfo(it, 0)
          }
          application.packageManager.getApplicationLabel(ai)
        } catch (e: PackageManager.NameNotFoundException) {
          it
        } as String
      )
    }

    if (distributors.isEmpty()) {
      distributors = listOf(
        Distributor(
          name = application.getString(R.string.UnifiedPushSettingsViewModel__no_distributor),
          applicationId = "",
        )
      )
      status = UnifiedPushStatus.NO_DISTRIBUTOR
    }

    return UnifiedPushSettingsState(
      airGaped = SignalStore.unifiedpush.airGaped,
      device = SignalStore.unifiedpush.device,
      distributors = distributors,
      selected = selected,
      endpoint = SignalStore.unifiedpush.endpoint,
      mollySocketUrl = SignalStore.unifiedpush.mollySocketUrl,
      mollySocketOk = SignalStore.unifiedpush.mollySocketFound,
      status = status ?: SignalStore.unifiedpush.status,
    )
  }

  fun setUnifiedPushAirGaped(airGaped: Boolean) {
    SignalStore.unifiedpush.airGaped = airGaped
    processNewStatus()
  }

  fun setUnifiedPushDistributor(distributor: String) {
    UnifiedPush.saveDistributor(application, distributor)
    UnifiedPush.registerApp(application)
    store.update { getState() }
  }

  fun setMollySocketUrl(url: String?) {
    SignalStore.unifiedpush.mollySocketUrl = if (url.isNullOrBlank()) {
      null
    } else if (url.last() != '/') {
      "$url/"
    } else {
      url
    }
    SignalStore.unifiedpush.pending = true
    EXECUTOR.enqueue {
      SignalStore.unifiedpush.mollySocketFound = try {
        MollySocketRequest.discoverMollySocketServer()
      } catch (e: Exception) {
        SignalStore.unifiedpush.mollySocketInternalError = true
        false
      }
      processNewStatus()
    }
  }

  private fun processNewStatus() {
    store.update { getState() }
    AppDependencies.jobManager.add(UnifiedPushRefreshJob())
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(UnifiedPushSettingsViewModel(application)))
    }
  }
}
