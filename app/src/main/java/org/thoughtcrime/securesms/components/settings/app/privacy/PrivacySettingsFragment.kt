package org.thoughtcrime.securesms.components.settings.app.privacy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.PassphraseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import org.thoughtcrime.securesms.components.TimeDurationPickerDialog
import org.thoughtcrime.securesms.components.settings.ClickPreference
import org.thoughtcrime.securesms.components.settings.ClickPreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.ExpirationUtil
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.time.Duration.Companion.seconds

private val TAG = Log.tag(PrivacySettingsFragment::class.java)

class PrivacySettingsFragment : DSLSettingsFragment(R.string.preferences__privacy) {

  private val passphraseLockTriggerValues by lazy { resources.getStringArray(R.array.pref_passphrase_lock_trigger_entries) }
  private val passphraseLockTriggerLabels by lazy { resources.getStringArray(R.array.pref_passphrase_lock_trigger_values) }

  private lateinit var viewModel: PrivacySettingsViewModel

  private val incognitoSummary: CharSequence by lazy {
    SpannableStringBuilder(getString(R.string.preferences__this_setting_is_not_a_guarantee))
      .append(" ")
      .append(
        SpanUtil.learnMore(requireContext(), ThemeUtil.getThemedColor(requireContext(), R.attr.signal_text_primary)) {
          CommunicationActions.openBrowserLink(requireContext(), getString(R.string.preferences__incognito_keyboard_learn_more))
        }
      )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshBlockedCount()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.registerFactory(ValueClickPreference::class.java, LayoutFactory(::ValueClickPreferenceViewHolder, R.layout.value_click_preference_item))

    val sharedPreferences = SecurePreferenceManager.getSecurePreferences(requireContext())
    val repository = PrivacySettingsRepository()
    val factory = PrivacySettingsViewModel.Factory(sharedPreferences, repository)
    viewModel = ViewModelProvider(this, factory)[PrivacySettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: PrivacySettingsState): DSLConfiguration {
    return configure {

      sectionHeaderPref(R.string.PrivacySettingsFragment__messaging)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__phone_number),
        summary = DSLSettingsText.from(R.string.preferences_app_protection__choose_who_can_see),
        onClick = {
          Navigation.findNavController(requireView())
            .safeNavigate(R.id.action_privacySettingsFragment_to_phoneNumberPrivacySettingsFragment)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__read_receipts),
        summary = DSLSettingsText.from(R.string.preferences__if_read_receipts_are_disabled_you_wont_be_able_to_see_read_receipts),
        isChecked = state.readReceipts,
        isEnabled = SignalStore.account.isPrimaryDevice,
        onClick = {
          viewModel.setReadReceiptsEnabled(!state.readReceipts)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__typing_indicators),
        summary = DSLSettingsText.from(R.string.preferences__if_typing_indicators_are_disabled_you_wont_be_able_to_see_typing_indicators),
        isChecked = state.typingIndicators,
        isEnabled = SignalStore.account.isPrimaryDevice,
        onClick = {
          viewModel.setTypingIndicatorsEnabled(!state.typingIndicators)
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from(R.string.PrivacySettingsFragment_data_at_rest))

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__passphrase_lock),
        summary = DSLSettingsText.from(R.string.preferences__protect_molly_database_with_a_passphrase),
        isChecked = state.passphraseLock,
        onToggle = { isChecked ->
          val mode = if (isChecked) ChangePassphraseDialogFragment.MODE_ENABLE else ChangePassphraseDialogFragment.MODE_DISABLE
          val dialog = ChangePassphraseDialogFragment.newInstance(mode)
          dialog.setMasterSecretChangedListener { masterSecret ->
            viewModel.setPassphraseLockEnabled(isChecked)
            (activity as PassphraseActivity).setMasterSecret(masterSecret)
            ConversationUtil.refreshRecipientShortcuts()
          }
          dialog.show(parentFragmentManager, "ChangePassphraseDialogFragment")
          false
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__change_passphrase),
        isEnabled = state.passphraseLock,
        onClick = {
          val dialog = ChangePassphraseDialogFragment.newInstance()
          dialog.setMasterSecretChangedListener { masterSecret ->
            masterSecret.close()
            Toast.makeText(
              activity,
              R.string.preferences__passphrase_changed,
              Toast.LENGTH_LONG
            ).show()
          }
          dialog.show(parentFragmentManager, "ChangePassphraseDialogFragment")
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__duress_code),
        summary = DSLSettingsText.from(R.string.preferences__duress_code_summary),
        isEnabled = state.passphraseLock,
        isChecked = state.hasDuressCode,
        onToggle = { isChecked ->
          if (isChecked) {
            val dialog = ChangePassphraseDialogFragment.newInstance(ChangePassphraseDialogFragment.MODE_DURESS_SET)
            dialog.setMasterSecretChangedListener { masterSecret ->
              viewModel.setDuressCodeEnabled(isChecked)
            }
            dialog.show(parentFragmentManager, "ChangePassphraseDialogFragment")
          } else {
            viewModel.setDuressCodeEnabled(false)
          }
          false
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__change_duress_code),
        isEnabled = state.passphraseLock && state.hasDuressCode,
        onClick = {
          val dialog = ChangePassphraseDialogFragment.newInstance(ChangePassphraseDialogFragment.MODE_DURESS_SET)
          dialog.setMasterSecretChangedListener { masterSecret ->
            masterSecret.close()
            Toast.makeText(
              activity,
              R.string.preferences__duress_code_changed,
              Toast.LENGTH_LONG
            ).show()
          }
          dialog.show(parentFragmentManager, "ChangePassphraseDialogFragment")
        }
      )

      multiSelectPref(
        title = DSLSettingsText.from(R.string.preferences__automatic_lockdown),
        listItems = passphraseLockTriggerLabels,
        selected = passphraseLockTriggerValues.map { state.passphraseLockTriggerValues.contains(it) }.toBooleanArray(),
        isEnabled = state.passphraseLock,
        onSelected = {
          val resultSet = it.mapIndexed { index, selected -> if (selected) passphraseLockTriggerValues[index] else null }.filterNotNull().toSet()
          viewModel.setPassphraseLockTrigger(resultSet)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__device_lock_timeout),
        summary = DSLSettingsText.from(getDeviceLockTimeoutSummary(state.passphraseLockTimeout)),
        isEnabled = state.passphraseLock && PassphraseLockTriggerPreference(state.passphraseLockTriggerValues).isTimeoutEnabled,
        onClick = {
          childFragmentManager.clearFragmentResult(TimeDurationPickerDialog.RESULT_DURATION)
          childFragmentManager.clearFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION)
          childFragmentManager.setFragmentResultListener(TimeDurationPickerDialog.RESULT_DURATION, this@PrivacySettingsFragment) { _, bundle ->
            val timeoutSeconds = bundle.getLong(TimeDurationPickerDialog.RESULT_KEY_DURATION_SECONDS)
            viewModel.setPassphraseLockTimeout(timeoutSeconds)
          }
          TimeDurationPickerDialog.create(state.passphraseLockTimeout.seconds).show(childFragmentManager, null)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.BlockedUsersActivity__blocked_users)

      clickPref(
        title = DSLSettingsText.from(R.string.PrivacySettingsFragment__blocked),
        summary = DSLSettingsText.from(resources.getQuantityString(R.plurals.PrivacySettingsFragment__d_contacts, state.blockedCount, state.blockedCount)),
        onClick = {
          Navigation.findNavController(requireView())
            .safeNavigate(R.id.action_privacySettingsFragment_to_blockedUsersActivity)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__block_unknown),
        summary = DSLSettingsText.from(getString(R.string.preferences__block_users_youve_never_been_in_contact_with_and_who_are_not_saved_in_your_contacts)),
        isChecked = state.blockUnknown,
        onClick = {
          viewModel.setBlockUnknownEnabled(!state.blockUnknown)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.PrivacySettingsFragment__disappearing_messages)

      customPref(
        ValueClickPreference(
          value = DSLSettingsText.from(ExpirationUtil.getExpirationAbbreviatedDisplayValue(requireContext(), state.universalExpireTimer)),
          clickPreference = ClickPreference(
            title = DSLSettingsText.from(R.string.PrivacySettingsFragment__default_timer_for_new_changes),
            summary = DSLSettingsText.from(R.string.PrivacySettingsFragment__set_a_default_disappearing_message_timer_for_all_new_chats_started_by_you),
            onClick = {
              NavHostFragment.findNavController(this@PrivacySettingsFragment).safeNavigate(R.id.action_privacySettingsFragment_to_disappearingMessagesTimerSelectFragment)
            }
          )
        )
      )

      dividerPref()

      sectionHeaderPref(R.string.PrivacySettingsFragment__app_security)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__screen_lock),
        summary = DSLSettingsText.from(R.string.PrivacySettingsFragment__lock_molly_access_with_fingerprint_or_face_recognition),
        isChecked = state.biometricScreenLock,
        onClick = {
          onBiometricScreenLockClicked(!state.biometricScreenLock)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__screen_security),
        summary = DSLSettingsText.from(R.string.PrivacySettingsFragment__block_screenshots_in_the_recents_list_and_inside_the_app),
        isChecked = state.screenSecurity || ScreenLockController.alwaysSetSecureFlagOnResume,
        isEnabled = !ScreenLockController.alwaysSetSecureFlagOnResume,
        onClick = {
          viewModel.setScreenSecurityEnabled(!state.screenSecurity)
          WindowUtil.initializeScreenshotSecurity(requireContext(), requireActivity().window)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__incognito_keyboard),
        summary = DSLSettingsText.from(R.string.preferences__request_keyboard_to_disable),
        isChecked = state.incognitoKeyboard,
        onClick = {
          viewModel.setIncognitoKeyboard(!state.incognitoKeyboard)
        }
      )

      textPref(
        summary = DSLSettingsText.from(incognitoSummary)
      )

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__advanced),
        summary = DSLSettingsText.from(R.string.PrivacySettingsFragment__signal_message_and_calls),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_privacySettingsFragment_to_advancedPrivacySettingsFragment)
        }
      )
    }
  }

  private val biometricEnrollment = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    onBiometricEnrollFinished()
  }

  private fun onBiometricScreenLockClicked(enabled: Boolean) {
    if (enabled) {
      val status = BiometricDialogFragment.checkBiometricAvailability(
        requireContext(), BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED
      )
      when (status) {
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
          Toast.makeText(context, R.string.PrivacySettingsFragment__no_biometric_features_available_on_this_device, Toast.LENGTH_LONG).show()

        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
          if (Build.VERSION.SDK_INT >= 30) {
            try {
              biometricEnrollment.launch(getIntentForBiometricEnrollment())
            } catch (e: ActivityNotFoundException) {
              Log.w(TAG, "Failed to navigate to system settings.", e)
              Toast.makeText(requireContext(), R.string.PrivacySettingsFragment__failed_to_navigate_to_system_settings, Toast.LENGTH_SHORT).show()
            }
          } else {
            Toast.makeText(context, R.string.PrivacySettingsFragment__please_first_setup_your_biometrics_in_android_settings, Toast.LENGTH_LONG).show()
          }
        }

        else -> onBiometricEnrollFinished()
      }
    } else {
      setBiometricScreenLock(false)
    }
  }

  @RequiresApi(30)
  private fun getIntentForBiometricEnrollment(): Intent =
    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
      putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricDialogFragment.BIOMETRIC_AUTHENTICATORS_ALLOWED)
    }

  private fun onBiometricEnrollFinished() {
    BiometricDialogFragment.authenticate(
      requireActivity(),
      object : BiometricDialogFragment.Listener {
        override fun onSuccess(): Boolean {
          setBiometricScreenLock(true)
          return true
        }

        override fun onError(errString: CharSequence): Boolean {
          Toast.makeText(context, errString, Toast.LENGTH_LONG).show()
          return true
        }
      }
    )
  }

  private fun setBiometricScreenLock(enabled: Boolean) {
    viewModel.setBiometricScreenLock(enabled)
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireActivity().window)
  }

  private fun getDeviceLockTimeoutSummary(timeoutSeconds: Long): String {
    return if (timeoutSeconds <= 0) {
      getString(R.string.AppProtectionPreferenceFragment_instant)
    } else {
      ExpirationUtil.getExpirationDisplayValue(requireContext(), timeoutSeconds.toInt())
    }
  }

  private class ValueClickPreference(
    val value: DSLSettingsText,
    val clickPreference: ClickPreference
  ) : PreferenceModel<ValueClickPreference>(
    title = clickPreference.title,
    summary = clickPreference.summary,
    icon = clickPreference.icon,
    isEnabled = clickPreference.isEnabled
  ) {
    override fun areContentsTheSame(newItem: ValueClickPreference): Boolean {
      return super.areContentsTheSame(newItem) &&
        clickPreference == newItem.clickPreference &&
        value == newItem.value
    }
  }

  private class ValueClickPreferenceViewHolder(itemView: View) : PreferenceViewHolder<ValueClickPreference>(itemView) {
    private val clickPreferenceViewHolder = ClickPreferenceViewHolder(itemView)
    private val valueText: TextView = findViewById(R.id.value_client_preference_value)

    override fun bind(model: ValueClickPreference) {
      super.bind(model)
      clickPreferenceViewHolder.bind(model.clickPreference)
      valueText.text = model.value.resolve(context)
    }
  }
}
