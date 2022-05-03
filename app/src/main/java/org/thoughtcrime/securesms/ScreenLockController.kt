package org.thoughtcrime.securesms

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.inspector.WindowInspector
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.migrations.ApplicationMigrations
import org.thoughtcrime.securesms.util.ServiceUtil

private val TAG = Log.tag(ScreenLockController.javaClass)

object ScreenLockController {

  private const val SCREEN_LOCK_TIMEOUT_SHORT = 5_000
  private const val SCREEN_LOCK_TIMEOUT_LONG = 60_000

  private const val APP_BACKGROUNDED_EVENT_DELAY_MS = 700

  private const val VIEW_ALWAYS_VISIBLE = 1
  private const val VIEW_VISIBILITY_CHANGED = 2

  private var graceTimeElapsed: Long = 0

  @JvmStatic
  var autoLock: Boolean = false

  @JvmStatic
  var lockScreenAtStart: Boolean = false

  @JvmStatic
  fun blankScreen() = setScreenContentVisibility(false)

  @JvmStatic
  fun unBlankScreen() = setScreenContentVisibility(true)

  // Cannot set FLAG_SECURE only at onPause() due to a bug in gesture navigation:
  // https://issuetracker.google.com/issues/123205795
  @JvmStatic
  val alwaysSetSecureFlagOnResume: Boolean
    get() = autoLock

  @JvmStatic
  fun shouldLockScreenAtStart(): Boolean {
    if (lockScreenAtStart) {
      val left = graceTimeElapsed - SystemClock.elapsedRealtime()
      if (left >= 0) {
        lockScreenAtStart = false
      } else {
        Log.v(TAG, "Timeout expired: ${if (graceTimeElapsed > 0) "${-left} ms ago" else "now"}")
      }
    }
    return lockScreenAtStart
  }

  @JvmStatic
  fun enableAutoLock(enabled: Boolean) {
    Log.d(TAG, "Auto-lock ${if (enabled) "enabled" else "disabled"}")
    autoLock = enabled
    lockScreenAtStart = enabled
  }

  @JvmStatic
  fun onAppBackgrounded(context: Context) {
    if (autoLock) {
      startScreenLock(timeoutFor(context) - APP_BACKGROUNDED_EVENT_DELAY_MS)
      clearReplyActionFromNotifications(context)
    }
  }

  private fun startScreenLock(timeout: Int) {
    if (!lockScreenAtStart) {
      lockScreenAtStart = true
      graceTimeElapsed = SystemClock.elapsedRealtime() + timeout
    }
  }

  @SuppressLint("NewApi")
  private fun timeoutFor(context: Context): Int {
    val taskActivities = taskActivitiesRunningOnTop(context)
    return when {
      taskActivities.any { context.packageName != it.packageName } -> SCREEN_LOCK_TIMEOUT_LONG
      else -> SCREEN_LOCK_TIMEOUT_SHORT
    }
  }

  @SuppressLint("NewApi")
  private fun taskActivitiesRunningOnTop(context: Context): List<ComponentName> =
    ServiceUtil.getActivityManager(context).appTasks
      .map { it.taskInfo }
      .filter { it.isRunning }
      .mapNotNull { it.topActivity }

  private fun clearReplyActionFromNotifications(context: Context) {
    if (!ApplicationMigrations.isUpdate(context) && SignalStore.settings().messageNotificationsPrivacy.isDisplayMessage) {
      SignalExecutors.BOUNDED.execute {
        ApplicationDependencies.getMessageNotifier().updateNotification(context)
      }
    }
  }

  @Synchronized
  private fun setScreenContentVisibility(visible: Boolean) {
    getGlobalWindowViews().forEach { decorView ->
      decorView.findViewById<View>(android.R.id.content)?.let { content ->
        if (decorView.alwaysVisible) {
          content.visibility = View.VISIBLE
        } else if (visible) {
          if (content.getTag(R.id.screen_lock_view_tag) == VIEW_VISIBILITY_CHANGED) {
            content.setTag(R.id.screen_lock_view_tag, null)
            content.visibility = View.VISIBLE
          }
        } else {
          content.setTag(R.id.screen_lock_view_tag, VIEW_VISIBILITY_CHANGED)
          content.visibility = View.INVISIBLE
        }
      }
    }
  }

  private var View.alwaysVisible: Boolean
    get() {
      val tag = getTag(R.id.screen_lock_view_tag)
      return tag == VIEW_ALWAYS_VISIBLE
    }
    set(value) {
      val tag = if (value) VIEW_ALWAYS_VISIBLE else null
      setTag(R.id.screen_lock_view_tag, tag)
    }

  @JvmStatic
  fun setShowWhenLocked(window: Window?, showWhenLocked: Boolean) {
    window?.decorView?.alwaysVisible = showWhenLocked
  }

  private fun getGlobalWindowViews(): List<View> =
    if (Build.VERSION.SDK_INT >= 29) {
      WindowInspector.getGlobalWindowViews()
    } else {
      WindowInspectorLegacy.getGlobalWindowViews()
    }
}

@SuppressLint("PrivateApi")
object WindowInspectorLegacy {
  private val wmClass = Class.forName("android.view.WindowManagerGlobal")
  private val lockField = wmClass.getDeclaredField("mLock").apply { isAccessible = true }
  private val viewsField = wmClass.getDeclaredField("mViews").apply { isAccessible = true }
  private val getInstanceMethod = wmClass.getMethod("getInstance")

  fun getGlobalWindowViews(): List<View> {
    val wm = getInstanceMethod.invoke(null)
    val mLock = lockField[wm]!!
    synchronized(mLock) {
      @Suppress("UNCHECKED_CAST")
      return ArrayList(viewsField[wm] as ArrayList<View>)
    }
  }
}
