package org.thoughtcrime.securesms.components.settings.app.notifications

import android.net.Uri
import org.thoughtcrime.securesms.keyvalue.SettingsValues.NotificationDeliveryMethod

data class NotificationsSettingsState(
  val isLinkedDevice: Boolean,
  val messageNotificationsState: MessageNotificationsState,
  val callNotificationsState: CallNotificationsState,
  val notifyWhenContactJoinsSignal: Boolean,
  val notificationDeliveryMethod: NotificationDeliveryMethod
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
)

data class CallNotificationsState(
  val notificationsEnabled: Boolean,
  val canEnableNotifications: Boolean,
  val ringtone: Uri,
  val vibrateEnabled: Boolean
)
