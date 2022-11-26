package im.molly.unifiedpush.components.settings.app.notifications

import android.content.DialogInterface
import android.content.res.Resources
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

  private fun getConfiguration(state: UnifiedPushSettingsState): DSLConfiguration {
    return configure {
      textPref(
        title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__status)),
        summary = DSLSettingsText.from(getStatusSummary(state)),
      )

      switchPref(
        title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__enabled),
        isChecked = state.enabled,
        onClick = {
          viewModel.setUnifiedPushEnabled(!state.enabled)
        }
      )

      if (state.enabled) {

        switchPref(
          title = DSLSettingsText.from(getString(R.string.UnifiedPushSettingsFragment__air_gaped)),
          isChecked = state.airGaped,
          onClick = {
            viewModel.setUnifiedPushAirGaped(!state.airGaped)
          }
        )

        val distributors = {
          radioListPref(
            title = DSLSettingsText.from(R.string.UnifiedPushSettingsFragment__method),
            listItems = state.distributors.map { it.name }.toTypedArray(),
            selected = state.selected,
            onSelected = {
              viewModel.setUnifiedPushDistributor(state.distributors[it].applicationId)
            },
          )
        }

        if (state.airGaped) {

          distributors()

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

          distributors()
        }
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
    if (!state.enabled) {
      return getString(R.string.UnifiedPushSettingsFragment__status_summary_disabled)
    }
    if (state.airGaped) {
      return getString(R.string.UnifiedPushSettingsFragment__status_summary_air_gaped)
    }
    if (state.mollySocketUrl.isNullOrBlank()) {
      return getString(R.string.UnifiedPushSettingsFragment__status_summary_mollysocket_url_missing)
    }
    return when (state.mollySocketOk) {
      null -> getString(R.string.UnifiedPushSettingsFragment__status_summary_pending)
      true -> getString(R.string.UnifiedPushSettingsFragment__ok)
      false -> getString(R.string.UnifiedPushSettingsFragment__status_summary_mollysocket_server_not_found)
    }
  }

  private fun getMollySocketUrlIcon(state: UnifiedPushSettingsState): DSLSettingsIcon? {
    if (state.mollySocketUrl == null || state.mollySocketOk == null ) return null
    return if (state.mollySocketOk) {
      DSLSettingsIcon.from(R.drawable.ic_check_20)
    } else {
      DSLSettingsIcon.from(R.drawable.ic_alert)
    }
  }
}
