package org.thoughtcrime.securesms.screenlock

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.BaseActivity
import java.util.Collections
import java.util.IdentityHashMap

/** Counts visible activities rather than waiting for ProcessLifecycleOwner's 700 ms debounce. */
internal class ScreenLockVisibilityObserver(application: Application) : Application.ActivityLifecycleCallbacks {
  private val visible = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
  private val guardedWindows = IdentityHashMap<Activity, Int>()
  val hasVisibleActivities: Boolean get() = visible.isNotEmpty()

  init {
    application.registerActivityLifecycleCallbacks(this)
    ContextCompat.registerReceiver(application, object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) ScreenLockController.onScreenOff(context)
      }
    }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
  }

  fun starting(activity: Activity) {
    visible.add(activity)
    resuming(activity)
  }

  private fun resuming(activity: Activity) {
    if (activity !is BaseActivity || activity.useScreenLock()) ScreenLockController.onActivityResuming()
    if (activity !is BaseActivity) protectWindow(activity)
  }

  private fun protectWindow(activity: Activity) {
    if (activity.isFinishing || activity.isDestroyed) return
    if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!ScreenLockController.autoLock)
    if (ScreenLockController.autoLock && Build.VERSION.SDK_INT < 33) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (!ScreenLockController.isAccessAllowed()) {
      val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      if (!guardedWindows.containsKey(activity)) guardedWindows[activity] = activity.window.attributes.flags and flags
      activity.window.addFlags(flags)
      ScreenLockController.hideProtectedWindow(activity.window)
      ScreenLockController.blankScreen()
    } else {
      guardedWindows.remove(activity)?.let { original ->
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        activity.window.addFlags(original)
      }
      ScreenLockController.revealProtectedWindow(activity.window)
      ScreenLockController.unBlankScreen()
    }
  }

  fun authenticationChanged() {
    Handler(Looper.getMainLooper()).post {
      visible.toList().forEach { if (it is BaseActivity) it.onScreenLockStateChanged() else protectWindow(it) }
    }
  }

  fun stopping(activity: Activity) {
    if (visible.remove(activity) && visible.isEmpty() && !activity.isChangingConfigurations) {
      ScreenLockController.onActivitiesHidden(activity)
    }
  }

  override fun onActivityStarted(activity: Activity) = starting(activity)
  override fun onActivityResumed(activity: Activity) {
    resuming(activity)
    if (activity !is BaseActivity && !ScreenLockController.isAccessAllowed() && !activity.isFinishing) {
      activity.startActivity(Intent(activity, ScreenLockGateActivity::class.java))
    }
  }
  override fun onActivityStopped(activity: Activity) = stopping(activity)
  override fun onActivityDestroyed(activity: Activity) { stopping(activity); guardedWindows.remove(activity) }
  override fun onActivityCreated(activity: Activity, state: Bundle?) {
    if (activity !is BaseActivity) protectWindow(activity)
  }
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
