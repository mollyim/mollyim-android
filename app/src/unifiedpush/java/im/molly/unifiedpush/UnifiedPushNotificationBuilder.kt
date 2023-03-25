package im.molly.unifiedpush

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels

class UnifiedPushNotificationBuilder(val context: Context) {

  companion object {
    private const val NOTIFICATION_ID = 51215
  }

  private val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle(context.getString(R.string.NotificationDeliveryMethod__unifiedpush))
    .setContentIntent(null)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

  private fun getNotification(content: String): Notification {
    return builder.setContentText(content).setStyle(
      NotificationCompat.BigTextStyle()
        .bigText(content)
    ).build()
  }

  fun setNotificationDeviceLimitExceeded(deviceLimit: Int) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_device_limit_hit, deviceLimit - 1)))
  }

  fun setNotificationMollySocketRegistrationChanged() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_registration_changed)))
  }

  fun setNotificationEndpointChangedAirGapped() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__endpoint_changed_air_gapped)))
  }

  fun setNotificationRegistrationFailed() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__registration_failed)))
  }

  fun setNotificationTest() {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .notify(NOTIFICATION_ID, getNotification(context.getString(R.string.UnifiedPushNotificationBuilder__this_is_a_test_notification)))
  }
}
