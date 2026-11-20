package org.thoughtcrime.securesms.notifications

import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.RestoreNotificationsJob
import org.thoughtcrime.securesms.service.ExportedBroadcastReceiver

/** The system drops our posted notifications on app update and reboot, and nothing else puts them back. */
class RestoreNotificationsReceiver : ExportedBroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(RestoreNotificationsReceiver::class)
  }

  override fun onReceiveUnlock(context: Context, intent: Intent) {
    Log.i(TAG, "Enqueuing notification restore after ${intent.action}")
    RestoreNotificationsJob.enqueue()
  }
}
