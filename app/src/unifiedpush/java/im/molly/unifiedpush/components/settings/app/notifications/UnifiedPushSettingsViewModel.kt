package im.molly.unifiedpush.components.settings.app.notifications

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPushSettingsViewModel(private val application: Application) : ViewModel() {

  private val store = Store(getState())

  val state: LiveData<UnifiedPushSettingsState> = store.stateLiveData
  var checkingServer = false

  private fun getState(): UnifiedPushSettingsState {
    val distributor = UnifiedPush.getDistributor(application)
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
          val ai = application.packageManager.getApplicationInfo(it, 0)
          /* When Android 13 will be supported:
          val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.getApplicationInfo(it,
              PackageManager.ApplicationInfoFlags.of(
                PackageManager.GET_META_DATA.toLong()
              )
            )
          } else {
            application.packageManager.getApplicationInfo(it, 0)
          }*/
          application.packageManager.getApplicationLabel(ai)
        } catch (e: PackageManager.NameNotFoundException) {
          it
        } as String)
    }

    if (distributors.isEmpty()) {
      distributors = listOf(Distributor(
        name = application.getString(R.string.UnifiedPushSettingsViewModel__no_distributor),
        applicationId = "",
      ))
    }

    return UnifiedPushSettingsState(
      enabled = SignalStore.unifiedpush().enabled,
      airGaped = SignalStore.unifiedpush().airGaped,
      device = SignalStore.unifiedpush().device,
      distributors = distributors,
      selected = selected,
      endpoint = SignalStore.unifiedpush().endpoint,
      mollySocketUrl = SignalStore.unifiedpush().mollySocketUrl,
      mollySocketOk = if (checkingServer) { null } else { SignalStore.unifiedpush().mollySocketOk },
    )
  }

  fun setUnifiedPushEnabled(enabled: Boolean) {
    SignalStore.unifiedpush().enabled = enabled
    if (enabled) {
      UnifiedPush.getDistributors(application).getOrNull(0)?.let {
        UnifiedPush.saveDistributor(application, it)
        UnifiedPush.registerApp(application)
        // Do not enable if there is no distributor
      } ?: return
    } else {
      UnifiedPush.unregisterApp(application)
    }
    SignalStore.unifiedpush().enabled = enabled
    processNewStatus()
    store.update { getState() }
  }

  fun setUnifiedPushAirGaped(airGaped: Boolean) {
    SignalStore.unifiedpush().airGaped = airGaped
    processNewStatus()
  }

  fun setUnifiedPushDistributor(distributor: String) {
    UnifiedPush.saveDistributor(application, distributor)
    UnifiedPush.registerApp(application)
    store.update { getState() }
  }

  fun setMollySocketUrl(url: String?) {
    SignalStore.unifiedpush().mollySocketUrl = if (url.isNullOrBlank()) {
      null
    } else if (url.last() != '/') {
      "$url/"
    } else {
      url
    }
    processNewStatus()
  }

  private fun processNewStatus() {
    if (with(SignalStore.unifiedpush()) {
        this.enabled && !this.airGaped && this.endpoint.isNullOrBlank()
      }) {
      checkingServer = true
      store.update { getState() }
      //TODO
      SignalStore.unifiedpush().mollySocketOk = true
      checkingServer = false
    }
    store.update { getState() }
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(UnifiedPushSettingsViewModel(application)))
    }
  }
}