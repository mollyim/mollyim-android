package im.molly.unifiedpush.components.settings.app.notifications

import android.content.DialogInterface
import android.content.res.Resources
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

  override fun bindAdapter(adapter: MappingAdapter) {
    val factory = UnifiedPushSettingsViewModel.Factory(requireActivity().application)

    viewModel = ViewModelProvider(this, factory)[UnifiedPushSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)

    viewModel.checkMollySocketFromStoredUrl()
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

      switchPref(
        title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__air_gapped)),
        summary = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__air_gapped_summary)),
        isChecked = state.airGapped,
        onClick = {
          viewModel.setUnifiedPushAirGapped(!state.airGapped)
        }
      )

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
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__server_url)),
          summary = DSLSettingsText.from(state.mollySocketUrl ?: getString(R.string.UnifiedPushSettingsFragment__no_server_url_summary)),
          iconEnd = getMollySocketUrlIcon(state),
          onClick = { urlDialog(state) },
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

  private fun urlDialog(state: UnifiedPushSettingsState) {
    val alertDialog = MaterialAlertDialogBuilder(requireContext())
    val input = EditText(requireContext()).apply {
      inputType = InputType.TYPE_TEXT_VARIATION_URI
      setText(state.mollySocketUrl)
    }
    alertDialog.setEditText(
      input
    )
    alertDialog.setPositiveButton(getString(android.R.string.ok)) { _: DialogInterface, _: Int ->
      val isValid = viewModel.setMollySocketUrl(input.text.toString())
      if (!isValid && input.text.isNotEmpty()) {
        Toast.makeText(requireContext(), R.string.UnifiedPushSettingsFragment__invalid_server_url, Toast.LENGTH_LONG).show()
      }
    }
    alertDialog.show()
  }

  private val Float.toPx: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

  private fun MaterialAlertDialogBuilder.setEditText(editText: EditText): MaterialAlertDialogBuilder {
    val container = FrameLayout(context)
    container.addView(editText)
    val containerParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
    val marginHorizontal = 48F
    val marginTop = 16F
    containerParams.topMargin = (marginTop / 2).toPx
    containerParams.leftMargin = marginHorizontal.toInt()
    containerParams.rightMargin = marginHorizontal.toInt()
    container.layoutParams = containerParams

    val superContainer = FrameLayout(context)
    superContainer.addView(container)

    setView(superContainer)

    return this
  }

  @StringRes
  private fun getStatusSummary(state: UnifiedPushSettingsState): Int {
    return when {
      state.distributors.isEmpty() -> R.string.UnifiedPushSettingsFragment__status_summary_no_distributor
      state.selected == -1 -> R.string.UnifiedPushSettingsFragment__status_summary_distributor_not_selected
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

  private fun getMollySocketUrlIcon(state: UnifiedPushSettingsState): DSLSettingsIcon? {
    return when (state.serverUnreachable) {
      true -> DSLSettingsIcon.from(R.drawable.ic_alert)
      false -> DSLSettingsIcon.from(R.drawable.ic_check_20)
      else -> null
    }
  }
}
