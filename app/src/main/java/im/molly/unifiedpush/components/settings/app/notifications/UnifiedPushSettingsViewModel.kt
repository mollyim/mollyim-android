package im.molly.unifiedpush.components.settings.app.notifications

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import im.molly.unifiedpush.MollySocketRepository
import im.molly.unifiedpush.model.MollySocket
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.UnifiedPushRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.thoughtcrime.securesms.util.livedata.Store
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPushSettingsViewModel(private val application: Application) : ViewModel() {

  private val store = Store(getState())
  private val executor = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)

  private var serverUnreachable: Boolean? = null

  val state: LiveData<UnifiedPushSettingsState> = store.stateLiveData

  fun refresh() {
    store.update { getState() }
  }

  fun initializeMollySocket(mollySocket: MollySocket) {
    SignalStore.unifiedpush.apply {
      airGapped = mollySocket is MollySocket.AirGapped
      mollySocketUrl = (mollySocket as? MollySocket.WebServer)?.url
      mollySocketVapid = mollySocket.vapid
    }
  }

  private fun refreshAndUpdateRegistration(pingOnRegister: Boolean = false) {
    refresh()
    AppDependencies.jobManager.add(UnifiedPushRefreshJob(pingOnRegister))
  }

  private fun getState(): UnifiedPushSettingsState {
    val distributorIds = UnifiedPush.getDistributors(application)
    val saved = UnifiedPush.getSavedDistributor(application)

    val selected = distributorIds.indexOfFirst { it == saved }

    val distributors = distributorIds.map { appId ->
      val name = try {
        val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          application.packageManager.getApplicationInfo(
            appId, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
          )
        } else {
          application.packageManager.getApplicationInfo(appId, 0)
        }
        application.packageManager.getApplicationLabel(ai)
      } catch (e: PackageManager.NameNotFoundException) {
        appId
      } as String

      Distributor(
        applicationId = appId,
        name = name,
      )
    }

    val mollySocketUrl = SignalStore.unifiedpush.mollySocketUrl

    return UnifiedPushSettingsState(
      airGapped = SignalStore.unifiedpush.airGapped,
      device = SignalStore.unifiedpush.device,
      aci = SignalStore.account.aci?.toString(),
      registrationStatus = SignalStore.unifiedpush.registrationStatus,
      distributors = distributors,
      selected = selected,
      endpoint = SignalStore.unifiedpush.endpoint,
      mollySocketUrl = mollySocketUrl,
      serverUnreachable = serverUnreachable,
    )
  }

  fun setUnifiedPushAirGapped(airGapped: Boolean) {
    SignalStore.unifiedpush.lastReceivedTime = 0
    SignalStore.unifiedpush.airGapped = airGapped
    refreshAndUpdateRegistration()
  }

  fun setUnifiedPushDistributor(distributor: String) {
    SignalStore.unifiedpush.endpoint = null
    UnifiedPush.saveDistributor(application, distributor)
    refreshAndUpdateRegistration()
  }

  fun setMollySocketUrl(url: String?): Boolean {
    SignalStore.unifiedpush.lastReceivedTime = 0

    val normalizedUrl = if (url?.lastOrNull() != '/') "$url/" else url ?: ""
    val httpUrl = normalizedUrl.toHttpUrlOrNull()

    return if (httpUrl != null) {
      SignalStore.unifiedpush.mollySocketUrl = normalizedUrl
      checkMollySocketServer(normalizedUrl)
      true
    } else {
      SignalStore.unifiedpush.mollySocketUrl = null
      serverUnreachable = null
      false
    }.also {
      refresh()
    }
  }

  fun checkMollySocketFromStoredUrl() {
    checkMollySocketServer(SignalStore.unifiedpush.mollySocketUrl ?: return)
  }

  private fun checkMollySocketServer(url: String) {
    executor.enqueue {
      val found = runCatching {
        MollySocketRepository.discoverMollySocketServer(url.toHttpUrl())
      }.getOrElse { false }

      // Update server reachability status
      serverUnreachable = !found
      refreshAndUpdateRegistration()

      if (!found) {
        showServerNotFoundToast()
      }
    }
  }

  private fun showServerNotFoundToast() {
    ThreadUtil.runOnMain {
      Toast.makeText(
        application,
        R.string.UnifiedPushSettingsViewModel__mollysocket_server_not_found,
        Toast.LENGTH_LONG
      ).show()
    }
  }

  fun pingMollySocket() {
    refreshAndUpdateRegistration(pingOnRegister = true)
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(UnifiedPushSettingsViewModel(application)))
    }
  }
}
