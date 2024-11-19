package org.thoughtcrime.securesms.components.settings.app.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.molly.unifiedpush.UnifiedPushDefaultDistributorLinkActivity
import im.molly.unifiedpush.components.settings.app.notifications.MollySocketQrScannerActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.PromptBatterySaverDialogFragment
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.RadioListPreference
import org.thoughtcrime.securesms.components.settings.RadioListPreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.Banner
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.events.PushServiceEvent
import org.thoughtcrime.securesms.keyvalue.SettingsValues.NotificationDeliveryMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.TurnOnNotificationsBottomSheet
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.RingtoneUtil
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val MESSAGE_SOUND_SELECT: Int = 1
private const val CALL_RINGTONE_SELECT: Int = 2
private val TAG = Log.tag(NotificationsSettingsFragment::class.java)

class NotificationsSettingsFragment : DSLSettingsFragment(R.string.preferences__notifications) {

  private val repeatAlertsValues by lazy { resources.getStringArray(R.array.pref_repeat_alerts_values) }
  private val repeatAlertsLabels by lazy { resources.getStringArray(R.array.pref_repeat_alerts_entries) }

  private val notificationPrivacyValues by lazy { resources.getStringArray(R.array.pref_notification_privacy_values) }
  private val notificationPrivacyLabels by lazy { resources.getStringArray(R.array.pref_notification_privacy_entries) }

  private val notificationPriorityValues by lazy { resources.getStringArray(R.array.pref_notification_priority_values) }
  private val notificationPriorityLabels by lazy { resources.getStringArray(R.array.pref_notification_priority_entries) }

  private val ledColorValues by lazy { resources.getStringArray(R.array.pref_led_color_values) }
  private val ledColorLabels by lazy { resources.getStringArray(R.array.pref_led_color_entries) }

  private val ledBlinkValues by lazy { resources.getStringArray(R.array.pref_led_blink_pattern_values) }
  private val ledBlinkLabels by lazy { resources.getStringArray(R.array.pref_led_blink_pattern_entries) }

  private lateinit var viewModel: NotificationsSettingsViewModel

  private val args: NotificationsSettingsFragmentArgs by navArgs()

  private val linkDefaultDistributorLauncher: ActivityResultLauncher<Unit> =
    registerForActivityResult(UnifiedPushDefaultDistributorLinkActivity.Contract()) { success ->
      if (success != true) {
        // If there are no distributors or
        // if there are multiple distributors installed, but none of them follow the last
        // specifications,
        // we try to fall back to the first we found.
        viewModel.selectFirstDistributor()
      }
      navigateToUnifiedPushSettings()
    }

  private val qrScanLauncher: ActivityResultLauncher<Unit> =
    registerForActivityResult(MollySocketQrScannerActivity.Contract()) { mollySocket ->
      if (mollySocket != null) {
        viewModel.initializeMollySocket(mollySocket)
        viewModel.setPreferredNotificationMethod(NotificationDeliveryMethod.UNIFIEDPUSH)
        linkDefaultDistributorLauncher.launch()
      }
    }

  private val layoutManager: LinearLayoutManager?
    get() = recyclerView?.layoutManager as? LinearLayoutManager

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == MESSAGE_SOUND_SELECT && resultCode == Activity.RESULT_OK && data != null) {
      val uri: Uri? = data.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
      viewModel.setMessageNotificationsSound(uri)
    } else if (requestCode == CALL_RINGTONE_SELECT && resultCode == Activity.RESULT_OK && data != null) {
      val uri: Uri? = data.getParcelableExtraCompat(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
      viewModel.setCallRingtone(uri)
    }
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.registerFactory(
      LedColorPreference::class.java,
      LayoutFactory(::LedColorPreferenceViewHolder, R.layout.dsl_preference_item)
    )

    Banner.register(adapter)

    val sharedPreferences = SecurePreferenceManager.getSecurePreferences(requireContext())
    val factory = NotificationsSettingsViewModel.Factory(sharedPreferences)

    viewModel = ViewModelProvider(this, factory)[NotificationsSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())

      val errorCode = viewModel.state.value?.playServicesErrorCode
      if (errorCode != null && errorCode != ConnectionResult.SUCCESS) {
        layoutManager?.scrollToPosition(adapter.itemCount - 1)
        showPlayServicesErrorDialog(errorCode)
        viewModel.setPlayServicesErrorCode(null)
      }
    }

    viewModel.setPlayServicesErrorCode(args.playServicesErrorCode)

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPushServiceEvent(event: PushServiceEvent) {
    viewModel.refresh()
  }

  private fun getConfiguration(state: NotificationsSettingsState): DSLConfiguration {
    return configure {
      if (!state.messageNotificationsState.canEnableNotifications) {
        customPref(
          Banner.Model(
            textId = R.string.NotificationSettingsFragment__to_enable_notifications,
            actionId = R.string.NotificationSettingsFragment__turn_on,
            onClick = {
              TurnOnNotificationsBottomSheet.turnOnSystemNotificationsFragment(requireContext()).show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
            }
          )
        )
      }

      sectionHeaderPref(R.string.NotificationsSettingsFragment__messages)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        isEnabled = state.messageNotificationsState.canEnableNotifications,
        isChecked = state.messageNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setMessageNotificationsEnabled(!state.messageNotificationsState.notificationsEnabled)
        }
      )

      if (Build.VERSION.SDK_INT >= 30) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__customize),
          summary = DSLSettingsText.from(R.string.preferences__change_sound_and_vibration),
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            NotificationChannels.getInstance().openChannelSettings(requireActivity(), NotificationChannels.getInstance().messagesChannel, null)
          }
        )
      } else {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__sound),
          summary = DSLSettingsText.from(getRingtoneSummary(state.messageNotificationsState.sound)),
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            launchMessageSoundSelectionIntent()
          }
        )

        switchPref(
          title = DSLSettingsText.from(R.string.preferences__vibrate),
          isChecked = state.messageNotificationsState.vibrateEnabled,
          isEnabled = state.messageNotificationsState.notificationsEnabled,
          onClick = {
            viewModel.setMessageNotificationVibration(!state.messageNotificationsState.vibrateEnabled)
          }
        )

        customPref(
          LedColorPreference(
            colorValues = ledColorValues,
            radioListPreference = RadioListPreference(
              title = DSLSettingsText.from(R.string.preferences__led_color),
              listItems = ledColorLabels,
              selected = ledColorValues.indexOf(state.messageNotificationsState.ledColor),
              isEnabled = state.messageNotificationsState.notificationsEnabled,
              onSelected = {
                viewModel.setMessageNotificationLedColor(ledColorValues[it])
              }
            )
          )
        )

        if (!NotificationChannels.supported()) {
          radioListPref(
            title = DSLSettingsText.from(R.string.preferences__pref_led_blink_title),
            listItems = ledBlinkLabels,
            selected = ledBlinkValues.indexOf(state.messageNotificationsState.ledBlink),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onSelected = {
              viewModel.setMessageNotificationLedBlink(ledBlinkValues[it])
            }
          )
        }
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__in_chat_sounds),
        isChecked = state.messageNotificationsState.inChatSoundsEnabled,
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setMessageNotificationInChatSoundsEnabled(!state.messageNotificationsState.inChatSoundsEnabled)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__repeat_alerts),
        listItems = repeatAlertsLabels,
        selected = repeatAlertsValues.indexOf(state.messageNotificationsState.repeatAlerts.toString()),
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onSelected = {
          viewModel.setMessageRepeatAlerts(repeatAlertsValues[it].toInt())
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__show),
        listItems = notificationPrivacyLabels,
        selected = notificationPrivacyValues.indexOf(state.messageNotificationsState.messagePrivacy),
        isEnabled = state.messageNotificationsState.notificationsEnabled,
        onSelected = {
          viewModel.setMessageNotificationPrivacy(notificationPrivacyValues[it])
        }
      )

      if (Build.VERSION.SDK_INT >= 23 && state.messageNotificationsState.troubleshootNotifications) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences_notifications__troubleshoot),
          isEnabled = true,
          onClick = {
            PromptBatterySaverDialogFragment.show(childFragmentManager)
          }
        )
      }

      if (Build.VERSION.SDK_INT < 30) {
        if (NotificationChannels.supported()) {
          clickPref(
            title = DSLSettingsText.from(R.string.preferences_notifications__priority),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onClick = {
              launchNotificationPriorityIntent()
            }
          )
        } else {
          radioListPref(
            title = DSLSettingsText.from(R.string.preferences_notifications__priority),
            listItems = notificationPriorityLabels,
            selected = notificationPriorityValues.indexOf(state.messageNotificationsState.priority.toString()),
            isEnabled = state.messageNotificationsState.notificationsEnabled,
            onSelected = {
              viewModel.setMessageNotificationPriority(notificationPriorityValues[it].toInt())
            }
          )
        }
      }

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__calls)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        isEnabled = state.callNotificationsState.canEnableNotifications,
        isChecked = state.callNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setCallNotificationsEnabled(!state.callNotificationsState.notificationsEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_notifications__ringtone),
        summary = DSLSettingsText.from(getRingtoneSummary(state.callNotificationsState.ringtone)),
        isEnabled = state.callNotificationsState.notificationsEnabled,
        onClick = {
          launchCallRingtoneSelectionIntent()
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__vibrate),
        isChecked = state.callNotificationsState.vibrateEnabled,
        isEnabled = state.callNotificationsState.notificationsEnabled,
        onClick = {
          viewModel.setCallVibrateEnabled(!state.callNotificationsState.vibrateEnabled)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__notification_profiles)

      clickPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__profiles),
        summary = DSLSettingsText.from(R.string.NotificationsSettingsFragment__create_a_profile_to_receive_notifications_only_from_people_and_groups_you_choose),
        onClick = {
          findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_notificationProfilesFragment)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__notify_when)

      switchPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__new_activity_while_locked),
        summary = DSLSettingsText.from(R.string.NotificationsSettingsFragment__receive_notifications_for_messages_or_missed_calls_when_the_app_is_locked),
        isChecked = state.notifyWhileLocked,
        onToggle = { isChecked ->
          if (isChecked && !state.canEnableNotifyWhileLocked) {
            MaterialAlertDialogBuilder(requireContext())
              .setMessage(R.string.NotificationsSettingsFragment__sorry_this_feature_requires_push_notifications_delivered_via_fcm_or_unifiedpush)
              .setPositiveButton(android.R.string.ok, null)
              .show()
            false
          } else {
            viewModel.setNotifyWhileLocked(isChecked)
            true
          }
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__contact_joins_signal),
        isChecked = state.notifyWhenContactJoinsSignal,
        onClick = {
          viewModel.setNotifyWhenContactJoinsSignal(!state.notifyWhenContactJoinsSignal)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.NotificationsSettingsFragment__push_notifications)

      textPref(
        summary = DSLSettingsText.from(R.string.NotificationsSettingsFragment__select_your_preferred_service_for_push_notifications)
      )

      val notificationMethods = NotificationDeliveryMethod.entries.filter { method ->
        when (method) {
          NotificationDeliveryMethod.FCM -> BuildConfig.USE_PLAY_SERVICES
          NotificationDeliveryMethod.WEBSOCKET -> true
          NotificationDeliveryMethod.UNIFIEDPUSH -> !state.isLinkedDevice
        }
      }

      val showAlertIcon = when (state.preferredNotificationMethod) {
        NotificationDeliveryMethod.FCM -> !state.canReceiveFcm
        NotificationDeliveryMethod.WEBSOCKET -> false
        NotificationDeliveryMethod.UNIFIEDPUSH -> !state.canReceiveUnifiedPush
      }

      radioListPref(
        title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__delivery_service),
        listItems = notificationMethods.map { resources.getString(it.stringId) }.toTypedArray(),
        selected = notificationMethods.indexOf(state.preferredNotificationMethod),
        iconEnd = if (showAlertIcon) DSLSettingsIcon.from(R.drawable.ic_alert, R.color.signal_alert_primary) else null,
        onSelected = {
          onNotificationMethodChanged(notificationMethods[it], state.preferredNotificationMethod)
        }
      )

      if (!state.isLinkedDevice) {
        clickPref(
          title = DSLSettingsText.from(R.string.NotificationsSettingsFragment__configure_unifiedpush),
          isEnabled = state.preferredNotificationMethod == NotificationDeliveryMethod.UNIFIEDPUSH,
          onClick = {
            navigateToUnifiedPushSettings()
          }
        )
      }
    }
  }

  private fun onNotificationMethodChanged(
    method: NotificationDeliveryMethod,
    previousMethod: NotificationDeliveryMethod
  ) {
    when (method) {
      NotificationDeliveryMethod.FCM -> viewModel.setPreferredNotificationMethod(method)
      NotificationDeliveryMethod.WEBSOCKET -> viewModel.setPreferredNotificationMethod(method)
      NotificationDeliveryMethod.UNIFIEDPUSH -> {
        if (method != previousMethod) {
          MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.NotificationsSettingsFragment__mollysocket_server)
            .setMessage(R.string.NotificationsSettingsFragment__to_use_unifiedpush_you_need_access_to_a_running_mollysocket)
            .setPositiveButton(R.string.RegistrationActivity_i_understand) { _, _ ->
              qrScanLauncher.launch()
            }
            .setNegativeButton(R.string.RegistrationActivity_cancel, null)
            .setNeutralButton(R.string.LearnMoreTextView_learn_more) { _, _ ->
              CommunicationActions.openBrowserLink(requireContext(), getString(R.string.mollysocket_setup_url))
            }
            .show()
        } else {
          navigateToUnifiedPushSettings()
        }
      }
    }
  }

  private fun navigateToUnifiedPushSettings() {
    findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_unifiedPushFragment)
  }

  private fun showPlayServicesErrorDialog(errorCode: Int) {
    val causeId = when (errorCode) {
      ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
        if (PlayServicesUtil.isGooglePlayPackageEnabled(context)) {
          R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable
        } else {
          R.string.NotificationsSettingsFragment__please_check_if_google_play_services_is_installed_and_enabled
        }
      }

      else -> R.string.NotificationsSettingsFragment__please_check_if_google_play_services_is_installed_and_enabled
    }

    MaterialAlertDialogBuilder(requireContext())
      .setNegativeButton(android.R.string.ok, null)
      .setMessage(
        getString(R.string.NotificationsSettingsFragment__an_error_occurred_while_registering_for_push_notifications_s, getString(causeId))
      )
      .show()
  }

  private fun getRingtoneSummary(uri: Uri): String {
    return if (TextUtils.isEmpty(uri.toString())) {
      getString(R.string.preferences__silent)
    } else {
      val tone: Ringtone? = RingtoneUtil.getRingtone(requireContext(), uri)
      if (tone != null) {
        try {
          tone.getTitle(requireContext()) ?: getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        } catch (e: SecurityException) {
          Log.w(TAG, "Unable to get title for ringtone", e)
          return getString(R.string.NotificationsSettingsFragment__unknown_ringtone)
        }
      } else {
        getString(R.string.preferences__default)
      }
    }
  }

  private fun launchMessageSoundSelectionIntent() {
    val current = SignalStore.settings.messageNotificationSound

    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
    intent.putExtra(
      RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
      Settings.System.DEFAULT_NOTIFICATION_URI
    )
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)

    openRingtonePicker(intent, MESSAGE_SOUND_SELECT)
  }

  @RequiresApi(26)
  private fun launchNotificationPriorityIntent() {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
    intent.putExtra(
      Settings.EXTRA_CHANNEL_ID,
      NotificationChannels.getInstance().messagesChannel
    )
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
    startActivity(intent)
  }

  private fun launchCallRingtoneSelectionIntent() {
    val current = SignalStore.settings.callRingtone

    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
    intent.putExtra(
      RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
      Settings.System.DEFAULT_RINGTONE_URI
    )
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)

    openRingtonePicker(intent, CALL_RINGTONE_SELECT)
  }

  @Suppress("DEPRECATION")
  private fun openRingtonePicker(intent: Intent, requestCode: Int) {
    try {
      startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.NotificationSettingsFragment__failed_to_open_picker, Toast.LENGTH_LONG).show()
    }
  }

  private class LedColorPreference(
    val colorValues: Array<String>,
    val radioListPreference: RadioListPreference
  ) : PreferenceModel<LedColorPreference>(
    title = radioListPreference.title,
    icon = radioListPreference.icon,
    summary = radioListPreference.summary
  ) {
    override fun areContentsTheSame(newItem: LedColorPreference): Boolean {
      return super.areContentsTheSame(newItem) && radioListPreference.areContentsTheSame(newItem.radioListPreference)
    }
  }

  private class LedColorPreferenceViewHolder(itemView: View) :
    PreferenceViewHolder<LedColorPreference>(itemView) {

    val radioListPreferenceViewHolder = RadioListPreferenceViewHolder(itemView)

    override fun bind(model: LedColorPreference) {
      super.bind(model)
      radioListPreferenceViewHolder.bind(model.radioListPreference)

      summaryView.visibility = View.GONE

      val circleDrawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.circle_tintable))
      circleDrawable.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))
      circleDrawable.colorFilter = model.colorValues[model.radioListPreference.selected].toColorFilter()

      if (ViewUtil.isLtr(itemView)) {
        titleView.setCompoundDrawables(null, null, circleDrawable, null)
      } else {
        titleView.setCompoundDrawables(circleDrawable, null, null, null)
      }
    }

    private fun String.toColorFilter(): ColorFilter {
      val color = when (this) {
        "green" -> ContextCompat.getColor(context, R.color.green_500)
        "red" -> ContextCompat.getColor(context, R.color.red_500)
        "blue" -> ContextCompat.getColor(context, R.color.blue_500)
        "yellow" -> ContextCompat.getColor(context, R.color.yellow_500)
        "cyan" -> ContextCompat.getColor(context, R.color.cyan_500)
        "magenta" -> ContextCompat.getColor(context, R.color.pink_500)
        "white" -> ContextCompat.getColor(context, R.color.white)
        else -> ContextCompat.getColor(context, R.color.transparent)
      }

      return PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
  }
}
