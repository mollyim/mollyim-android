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
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.migrations.ApplicationMigrations
import org.thoughtcrime.securesms.util.ServiceUtil

private val TAG = Log.tag(ScreenLockController.javaClass)

object ScreenLockController {

  private const val SCREEN_LOCK_TIMEOUT_SHORT_MS = 7_000
  private const val SCREEN_LOCK_TIMEOUT_LONG_MS = 60_000

  private const val APP_BACKGROUNDED_EVENT_DELAY_MS = 700

  private var graceTimeElapsed: Long = 0

  @JvmStatic
  var autoLock: Boolean = false
    private set

  @JvmStatic
  var lockScreenAtStart: Boolean = false

  @JvmStatic
  fun blankScreen() = setAllViewsWithContentHidden(true)

  @JvmStatic
  fun unBlankScreen() = setAllViewsWithContentHidden(false)

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
    val activities = activitiesRunningOnTopOfTasks(context)
    return when {
      activities.any { context.packageName != it.packageName } -> SCREEN_LOCK_TIMEOUT_LONG_MS
      else -> SCREEN_LOCK_TIMEOUT_SHORT_MS
    }
  }

  private fun activitiesRunningOnTopOfTasks(context: Context): List<ComponentName> =
    ServiceUtil.getActivityManager(context).appTasks
      .map { it.taskInfo }
      .filter {
        if (Build.VERSION.SDK_INT >= 29) it.isRunning else true
      }
      .mapNotNull { it.topActivity }

  private fun clearReplyActionFromNotifications(context: Context) {
    if (!ApplicationMigrations.isUpdate(context) && SignalStore.settings.messageNotificationsPrivacy.isDisplayMessage) {
      SignalExecutors.BOUNDED.execute {
        AppDependencies.messageNotifier.updateNotification(context)
      }
    }
  }

  private fun setAllViewsWithContentHidden(hidden: Boolean) {
    getGlobalWindowViews()
      .mapNotNull { it?.findContent }
      .filter { !BiometricDialogFragment.isDialogViewAttachedTo(it) }
      .forEach {
        synchronized(this) {
          if (it.visibility == View.VISIBLE) {
            if (hidden && !it.alwaysVisible) {
              it.visibility = View.INVISIBLE
              it.overrideVisibleFlag = true
            } else {
              it.overrideVisibleFlag = false
            }
          } else if (!hidden && it.overrideVisibleFlag) {
            it.visibility = View.VISIBLE
            it.overrideVisibleFlag = false
          }
        }
      }
  }

  @JvmStatic
  fun showWhenLocked(window: Window?) {
    window?.decorView?.findContent?.alwaysVisible = true
  }

  @JvmStatic
  fun hideWhenLocked(window: Window?) {
    window?.decorView?.findContent?.alwaysVisible = false
  }

  private val View.findContent: View?
    get() = rootView.findViewById(android.R.id.content)

  private var View.overrideVisibleFlag: Boolean
    get() = (getTag(R.id.screen_lock_visibility_changed_tag) ?: false) as Boolean
    set(value) {
      setTag(R.id.screen_lock_visibility_changed_tag, value)
    }

  private var View.alwaysVisible: Boolean
    get() = (getTag(R.id.screen_lock_always_visible_tag) ?: false) as Boolean
    set(value) {
      setTag(R.id.screen_lock_always_visible_tag, value)
    }

  private fun getGlobalWindowViews(): List<View?> =
    if (Build.VERSION.SDK_INT >= 29) {
      WindowInspector.getGlobalWindowViews()
    } else {
      WindowInspectorLegacy.getGlobalWindowViews()
    }
}

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object WindowInspectorLegacy {
  private val wmClass = Class.forName("android.view.WindowManagerGlobal")
  private val lockField = wmClass.getDeclaredField("mLock").apply { isAccessible = true }
  private val viewsField = wmClass.getDeclaredField("mViews").apply { isAccessible = true }
  private val getInstanceMethod = wmClass.getMethod("getInstance")

  fun getGlobalWindowViews(): List<View?> {
    val wm = getInstanceMethod.invoke(null)
    val mLock = lockField[wm]!!
    synchronized(mLock) {
      @Suppress("UNCHECKED_CAST")
      return ArrayList(viewsField[wm] as ArrayList<View?>)
    }
  }
}
