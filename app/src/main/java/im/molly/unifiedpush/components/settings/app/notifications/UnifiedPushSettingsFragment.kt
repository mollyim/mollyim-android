package im.molly.unifiedpush.components.settings.app.notifications

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import im.molly.unifiedpush.model.RegistrationStatus
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.events.PushServiceEvent
import org.thoughtcrime.securesms.util.Util.writeTextToClipboard
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class UnifiedPushSettingsFragment : DSLSettingsFragment(R.string.NotificationDeliveryMethod__unifiedpush) {

  private lateinit var viewModel: UnifiedPushSettingsViewModel

  private val qrScanLauncher: ActivityResultLauncher<Unit> =
    registerForActivityResult(MollySocketQrScannerActivity.Contract()) { mollySocket ->
      if (mollySocket != null) {
        viewModel.setMollySocket(mollySocket)
      }
    }

  override fun bindAdapter(adapter: MappingAdapter) {
    val factory = UnifiedPushSettingsViewModel.Factory(requireActivity().application)

    viewModel = ViewModelProvider(this, factory)[UnifiedPushSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)

    viewModel.updateRegistration()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPushServiceEvent(event: PushServiceEvent) {
    viewModel.refresh()
  }

  private fun getConfiguration(state: UnifiedPushSettingsState): DSLConfiguration {
    return configure {
      textPref(
        title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__status)),
        summary = DSLSettingsText.from(getStatusSummary(state)),
      )

      clickPref(
        title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__mollysocket_server),
        summary = DSLSettingsText.from(
          if (state.airGapped) {
            getString(R.string.UnifiedPushSettingsFragment__mollysocket_server_air_gapped)
          } else {
            state.mollySocketUrl ?: "Error"
          }
        ),
        onClick = {
          qrScanLauncher.launch()
        },
      )

      if (state.distributors.isEmpty()) {
        textPref(
          title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__distributor_app),
          summary = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__none_available)
        )
      } else {
        radioListPref(
          title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__distributor_app),
          listItems = state.distributors.map { it.name }.toTypedArray(),
          selected = state.selected,
          onSelected = {
            viewModel.setUnifiedPushDistributor(state.distributors[it].applicationId)
          },
        )
      }

      dividerPref()

      if (state.airGapped) {
        val parameters = getServerParameters(state) ?: ""

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__server_parameters)),
          summary = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__tap_to_copy_to_clipboard)),
          iconEnd = DSLSettingsIcon.from(R.drawable.symbol_copy_android_24),
          isEnabled = parameters.isNotEmpty(),
          onClick = {
            writeTextToClipboard(requireContext(), "Server parameters", parameters)
          },
        )
      } else {
        val aciOrUnknown = state.aci ?: getString(R.string.Recipient_unknown)

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__account_id)),
          summary = DSLSettingsText.from(aciOrUnknown),
          iconEnd = DSLSettingsIcon.from(R.drawable.symbol_copy_android_24),
          onClick = {
            writeTextToClipboard(requireContext(), "Account ID", aciOrUnknown)
          },
        )

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__test_configuration)),
          summary = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__tap_to_request_a_test_notification_from_mollysocket)),
          onClick = {
            viewModel.pingMollySocket()
            Toast.makeText(context, getString(R.string.UnifiedPushSettingsFragment__a_test_notification_should_appear_in_a_few_moments), Toast.LENGTH_SHORT).show()
          },
        )
      }
    }
  }

  private fun getServerParameters(state: UnifiedPushSettingsState): String? {
    val aci = state.aci ?: return null
    val device = state.device ?: return null
    val endpoint = state.endpoint ?: return null
    return "connection add $aci ${device.deviceId} ${device.password} $endpoint"
  }

  @StringRes
  private fun getStatusSummary(state: UnifiedPushSettingsState): Int {
    return when {
      state.distributors.isEmpty() -> R.string.UnifiedPushSettingsFragment__status_summary_no_distributor
      state.selected == -1 -> R.string.UnifiedPushSettingsFragment__status_summary_distributor_not_selected
      state.selectedNotAck -> R.string.UnifiedPushSettingsFragment__status_summary_missing_endpoint
      state.endpoint == null -> R.string.UnifiedPushSettingsFragment__status_summary_missing_endpoint
      state.mollySocketUrl == null && !state.airGapped -> R.string.UnifiedPushSettingsFragment__status_summary_mollysocket_url_missing
      state.device == null -> R.string.UnifiedPushSettingsFragment__status_summary_linked_device_error
      else -> getRegistrationStatusSummary(state)
    }
  }

  @StringRes
  private fun getRegistrationStatusSummary(state: UnifiedPushSettingsState): Int {
    return if (state.airGapped) {
      when (state.registrationStatus) {
        RegistrationStatus.REGISTERED -> android.R.string.ok
        else -> R.string.UnifiedPushSettingsFragment__status_summary_air_gapped_pending
      }
    } else {
      when (state.registrationStatus) {
        RegistrationStatus.UNKNOWN,
        RegistrationStatus.PENDING -> R.string.UnifiedPushSettingsFragment__status_summary_pending

        RegistrationStatus.BAD_RESPONSE,
        RegistrationStatus.SERVER_ERROR -> R.string.UnifiedPushSettingsFragment__status_summary_bad_response

        RegistrationStatus.REGISTERED -> android.R.string.ok
        RegistrationStatus.FORBIDDEN_PASSWORD -> R.string.UnifiedPushSettingsFragment__status_summary_pending
        RegistrationStatus.FORBIDDEN_UUID -> R.string.UnifiedPushSettingsFragment__status_summary_forbidden_uuid
        RegistrationStatus.FORBIDDEN_ENDPOINT -> R.string.UnifiedPushSettingsFragment__status_summary_forbidden_endpoint
      }
    }
  }
}
