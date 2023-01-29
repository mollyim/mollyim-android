package im.molly.unifiedpush.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels

class UnifiedPushNotificationBuilder(val context: Context) {

  private val NOTIFICATION_ID_UNIFIEDPUSH = 51215

  private val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle(context.getString(R.string.UnifiedPushNotificationBuilder__title))
    .setContentIntent(null)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

  private fun getNotification(content: String): Notification {
    return builder.setContentText(content).setStyle(
      NotificationCompat.BigTextStyle()
        .bigText(content)
    ).build()
  }

  fun setNotificationMollySocketRegistrationChanged() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID_UNIFIEDPUSH, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_registration_changed)))
  }

  fun setNotificationEndpointChangedAirGaped() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID_UNIFIEDPUSH, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__endpoint_changed_airgaped)))
  }

  fun setNotificationEndpointChangedError() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID_UNIFIEDPUSH, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__endpoint_changed_error)))
  }

  fun setNotificationRegistrationFailed() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID_UNIFIEDPUSH, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__registration_failed)))
  }
}
