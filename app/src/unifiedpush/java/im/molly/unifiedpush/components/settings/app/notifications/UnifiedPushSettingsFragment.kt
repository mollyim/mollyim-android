package im.molly.unifiedpush.components.settings.app.notifications

import android.content.DialogInterface
import android.content.res.Resources
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.greenrobot.eventbus.EventBus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.Util.writeTextToClipboard
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class UnifiedPushSettingsFragment : DSLSettingsFragment(R.string.NotificationsSettingsFragment__unifiedpush) {

  private lateinit var viewModel: UnifiedPushSettingsViewModel

  override fun bindAdapter(adapter: MappingAdapter) {
    val factory = UnifiedPushSettingsViewModel.Factory(requireActivity().application)

    viewModel = ViewModelProvider(this, factory)[UnifiedPushSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }
  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(viewModel)
  }

  override fun onStop() {
    EventBus.getDefault().unregister(viewModel)
    super.onStop()
  }

  private fun getConfiguration(state: UnifiedPushSettingsState): DSLConfiguration {
    return configure {
      textPref(
        title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__status)),
        summary = DSLSettingsText.from(getStatusSummary(state)),
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__method),
        listItems = state.distributors.map { it.name }.toTypedArray(),
        selected = state.selected,
        onSelected = {
          viewModel.setUnifiedPushDistributor(state.distributors[it].applicationId)
        },
      )

      dividerPref()

      switchPref(
        title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__air_gaped)),
        summary = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__air_gaped_summary)),
        isChecked = state.airGaped,
        onClick = {
          viewModel.setUnifiedPushAirGaped(!state.airGaped)
        }
      )

      if (state.airGaped) {

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__server_parameters)),
          summary = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__click_to_copy)),
          iconEnd = DSLSettingsIcon.from(R.drawable.symbol_copy_android_24),
          onClick = { writeTextToClipboard(requireContext(), "Server parameters", getServerParameters(state)) },
        )
      } else {

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__account_id)),
          summary = DSLSettingsText.from(state.device?.uuid ?: getString(R.string.UnifiedPushSettingsFragment__unknown)),
          iconEnd = DSLSettingsIcon.from(R.drawable.symbol_copy_android_24),
          onClick = {
            writeTextToClipboard(requireContext(), "Account ID", state.device?.uuid ?: getString(R.string.UnifiedPushSettingsFragment__unknown))
          },
        )

        clickPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__server_url)),
          summary = DSLSettingsText.from(state.mollySocketUrl ?: getString(R.string.UnifiedPushSettingsFragment__no_server_url_summary)),
          iconEnd = getMollySocketUrlIcon(state),
          onClick = { urlDialog(state) },
        )
      }
    }
  }

  private fun getServerParameters(state: UnifiedPushSettingsState): String {
    val device = state.device ?: return getString(R.string.UnifiedPushSettingsFragment__no_device)
    val endpoint = state.endpoint ?: return getString(R.string.UnifiedPushSettingsFragment__no_endpoint)
    return "connection add ${device.uuid} ${device.deviceId} ${device.password} $endpoint websocket"
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
    alertDialog.setPositiveButton(getString(R.string.UnifiedPushSettingsFragment__ok)) { _: DialogInterface, _: Int ->
      viewModel.setMollySocketUrl(input.text.toString())
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

  private fun getStatusSummary(state: UnifiedPushSettingsState): String {
    return when (state.status) {
      UnifiedPushStatus.DISABLED -> getString(R.string.UnifiedPushSettingsFragment__status_summary_disabled)
      UnifiedPushStatus.LINK_DEVICE_ERROR -> getString(R.string.UnifiedPushSettingsFragment__status_summary_linked_device_error)
      UnifiedPushStatus.AIR_GAPED -> getString(R.string.UnifiedPushSettingsFragment__status_summary_air_gaped)
      UnifiedPushStatus.SERVER_NOT_FOUND_AT_URL -> getString(R.string.UnifiedPushSettingsFragment__status_summary_mollysocket_server_not_found)
      UnifiedPushStatus.MISSING_ENDPOINT -> getString(R.string.UnifiedPushSettingsFragment__status_summary_missing_endpoint)
      UnifiedPushStatus.FORBIDDEN_UUID -> getString(R.string.UnifiedPushSettingsFragment__status_summary_forbidden_uuid)
      UnifiedPushStatus.FORBIDDEN_ENDPOINT -> getString(R.string.UnifiedPushSettingsFragment__status_summary_forbidden_endpoint)
      UnifiedPushStatus.NO_DISTRIBUTOR -> getString(R.string.UnifiedPushSettingsFragment__status_summary_no_distributor)
      UnifiedPushStatus.PENDING -> getString(R.string.UnifiedPushSettingsFragment__status_summary_pending)
      UnifiedPushStatus.OK -> getString(R.string.UnifiedPushSettingsFragment__ok)
      UnifiedPushStatus.INTERNAL_ERROR -> getString(R.string.UnifiedPushSettingsFragment__status_summary_internal_error)
      UnifiedPushStatus.UNKNOWN -> getString(R.string.UnifiedPushSettingsFragment__status_summary_unknown_error)
    }
  }

  private fun getMollySocketUrlIcon(state: UnifiedPushSettingsState): DSLSettingsIcon? {
    if (state.mollySocketUrl.isNullOrBlank() || state.status == UnifiedPushStatus.PENDING) return null
    return if (state.mollySocketOk) {
      DSLSettingsIcon.from(R.drawable.ic_check_20)
    } else {
      DSLSettingsIcon.from(R.drawable.ic_alert)
    }
  }
}
