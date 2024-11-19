package im.molly.unifiedpush

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.NotificationChannels

class UnifiedPushNotificationBuilder(val context: Context) {

  companion object {
    private const val NOTIFICATION_ID = 51215
    private const val NOTIFICATION_TEST_ID = 51216
  }

  private val notificationManager = NotificationManagerCompat.from(context)

  private val builder: NotificationCompat.Builder =
    NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
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

  private fun notify(notificationId: Int, content: String) {
    val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    if (hasPermission) {
      notificationManager.notify(notificationId, getNotification(content))
    }
  }

  fun clearAlerts() {
    notificationManager.cancel(NOTIFICATION_ID)
  }

  fun setNotificationDeviceLimitExceeded(deviceLimit: Int) {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_device_limit_hit, deviceLimit - 1))
  }


  fun setNotificationMollySocketForbiddenEndpoint() {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_forbidden_endpoint))
  }

  fun setNotificationMollySocketForbiddenUuid() {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_forbidden_uuid))
  }

  fun setNotificationMollySocketForbiddenPassword() {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__mollysocket_forbidden_password))
  }

  fun setNotificationEndpointChangedAirGapped() {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__endpoint_changed_air_gapped))
  }

  fun setNotificationRegistrationFailed() {
    notify(NOTIFICATION_ID, context.getString(R.string.UnifiedPushNotificationBuilder__registration_failed))
  }

  fun setNotificationTest() {
    notify(NOTIFICATION_TEST_ID, context.getString(R.string.UnifiedPushNotificationBuilder__this_is_a_test_notification))
  }
}
