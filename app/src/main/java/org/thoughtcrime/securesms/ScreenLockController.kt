package org.thoughtcrime.securesms

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.inspector.WindowInspector
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.migrations.ApplicationMigrations
import org.thoughtcrime.securesms.screenlock.ScreenLockMode
import org.thoughtcrime.securesms.screenlock.ScreenLockPreferences
import org.thoughtcrime.securesms.screenlock.ScreenLockSession
import org.thoughtcrime.securesms.screenlock.ScreenLockVisibilityObserver
import org.thoughtcrime.securesms.screenlock.ScreenLockPinDialogFragment
import org.thoughtcrime.securesms.service.KeyCachingService
import java.security.SecureRandom

object ScreenLockController {

  private val session = ScreenLockSession(SecureRandom().nextLong())
  private var visibility: ScreenLockVisibilityObserver? = null

  @JvmStatic
  val autoLock: Boolean get() = synchronized(this) { session.enabled }

  @JvmStatic
  val lockScreenAtStart: Boolean get() = synchronized(this) { session.pendingLock }

  @JvmStatic
  fun initialize(context: Application) {
    configure(context, false)
    visibility = ScreenLockVisibilityObserver(context)
  }

  @JvmStatic
  @Synchronized
  fun configure(context: Context, authenticated: Boolean) {
    session.configure(ScreenLockPreferences.getMode(context) != ScreenLockMode.OFF, authenticated)
    visibility?.authenticationChanged()
  }

  @JvmStatic
  @Synchronized
  fun authenticationGeneration(): Long = session.authenticationGeneration

  @JvmStatic
  @Synchronized
  fun isCurrentAuthentication(generation: Long): Boolean = session.isCurrent(generation)

  @JvmStatic
  @Synchronized
  fun isAccessAllowed(): Boolean = !session.requiresAuthentication(SystemClock.elapsedRealtime())

  /** Recheck on the worker that performs a user action, not only when rendering its button. */
  @JvmStatic
  @Synchronized
  fun runIfAccessAllowed(generation: Long, action: Runnable): Boolean {
    if (!session.canRunAction(SystemClock.elapsedRealtime(), generation)) return false
    action.run()
    return true
  }

  @JvmStatic
  @Synchronized
  fun <T> withAccessAllowed(generation: Long, fallback: T, action: () -> T): T =
    if (session.canRunAction(SystemClock.elapsedRealtime(), generation)) action() else fallback

  @Synchronized
  fun runIfCurrentAuthentication(generation: Long, action: () -> Unit): Boolean {
    if (!session.isCurrent(generation)) return false
    action()
    return true
  }

  @JvmStatic
  @Synchronized
  fun completeAuthentication(generation: Long): Boolean {
    val completed = session.authenticate(generation)
    if (completed) visibility?.authenticationChanged()
    return completed
  }

  @JvmStatic
  @Synchronized
  fun forceLock() = session.forceLock()

  @JvmStatic
  fun onActivityStarting(activity: Activity) = visibility?.starting(activity)

  @JvmStatic
  fun onActivityStopping(activity: Activity) = visibility?.stopping(activity)

  @JvmStatic
  @Synchronized
  fun onActivityResuming() { session.resume(SystemClock.elapsedRealtime()) }

  @Synchronized
  fun onActivitiesHidden(context: Context) {
    hide(context, timeoutFor(context))
  }

  @Synchronized
  fun onScreenOff(context: Context) {
    hide(context, timeoutFor(context))
    blankScreen()
  }

  @JvmStatic
  @Synchronized
  fun onTaskRemoved(context: Context) {
    session.end()
    visibility?.authenticationChanged()
    if (autoLock) clearReplyActionFromNotifications(context)
  }

  private fun hide(context: Context, timeout: Long) {
    session.hide(SystemClock.elapsedRealtime(), timeout)
    if (autoLock) clearReplyActionFromNotifications(context)
  }

  @JvmStatic
  fun blankScreen() = setAllViewsWithContentHidden(true)

  @JvmStatic
  fun unBlankScreen() = setAllViewsWithContentHidden(false)

  /** Hide a library activity before its window is attached and appears in global window views. */
  @JvmStatic
  fun hideProtectedWindow(window: Window?) {
    window?.decorView?.findContent?.let {
      it.alwaysVisible = false
      if (it.visibility == View.VISIBLE) {
        it.visibility = View.INVISIBLE
        it.overrideVisibleFlag = true
      }
    }
  }

  @JvmStatic
  fun revealProtectedWindow(window: Window?) {
    window?.decorView?.findContent?.let {
      if (it.overrideVisibleFlag) {
        it.visibility = View.VISIBLE
        it.overrideVisibleFlag = false
      }
    }
  }

  // FLAG_SECURE cannot be applied only in onPause() due to a gesture navigation bug
  // that leaks content in the recents view before authentication (b/123205795).
  // On API 33+ recents screenshots are controlled via setRecentsScreenshotEnabled().
  @JvmStatic
  val alwaysSetSecureFlagOnResume: Boolean
    get() = autoLock && Build.VERSION.SDK_INT < 33

  @JvmStatic
  @Synchronized
  fun shouldLockScreenAtStart(): Boolean = session.resume(SystemClock.elapsedRealtime())

  @JvmStatic
  fun onAppBackgrounded(context: Context) {
    if (visibility?.hasVisibleActivities != true) {
      synchronized(this) { hide(context, timeoutFor(context)) }
    }
  }

  private fun timeoutFor(context: Context): Long = ScreenLockPreferences.getTimeoutSeconds(context) * 1_000

  private fun clearReplyActionFromNotifications(context: Context) {
    if (!KeyCachingService.isLocked() && !ApplicationMigrations.isUpdate(context) && SignalStore.settings.messageNotificationsPrivacy.isDisplayMessage) {
      SignalExecutors.BOUNDED.execute {
        AppDependencies.messageNotifier.updateNotification(context)
      }
    }
  }

  private fun setAllViewsWithContentHidden(hidden: Boolean) {
    getGlobalWindowViews()
      .mapNotNull { it?.findContent }
      .filter { !BiometricDialogFragment.isDialogViewAttachedTo(it) && it.findViewById<View>(R.id.screen_lock_pin_dialog) == null }
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

  @JvmStatic
  fun revealExemptWindow(window: Window?) {
    window?.decorView?.findContent?.let {
      it.alwaysVisible = true
      if (it.overrideVisibleFlag) {
        it.visibility = View.VISIBLE
        it.overrideVisibleFlag = false
      }
    }
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
