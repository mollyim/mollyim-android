package org.thoughtcrime.securesms.components.settings.app.notifications

import android.net.Uri
import org.thoughtcrime.securesms.keyvalue.SettingsValues.NotificationDeliveryMethod

data class NotificationsSettingsState(
  val messageNotificationsState: MessageNotificationsState,
  val callNotificationsState: CallNotificationsState,
  val notifyWhileLocked: Boolean,
  val canEnableNotifyWhileLocked: Boolean,
  val notifyWhenContactJoinsSignal: Boolean,
  val isLinkedDevice: Boolean,
  val preferredNotificationMethod: NotificationDeliveryMethod,
  val playServicesErrorCode: Int?,
  val canReceiveFcm: Boolean,
  val canReceiveUnifiedPush: Boolean
)

data class MessageNotificationsState(
  val notificationsEnabled: Boolean,
  val canEnableNotifications: Boolean,
  val sound: Uri,
  val vibrateEnabled: Boolean,
  val ledColor: String,
  val ledBlink: String,
  val inChatSoundsEnabled: Boolean,
  val repeatAlerts: Int,
  val messagePrivacy: String,
  val priority: Int,
  val troubleshootNotifications: Boolean
)

data class CallNotificationsState(
  val notificationsEnabled: Boolean,
  val canEnableNotifications: Boolean,
  val ringtone: Uri,
  val vibrateEnabled: Boolean
)
