package org.thoughtcrime.securesms.screenlock

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.ScreenLockController

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ScreenLockWindowTest {
  @Test fun contentIsHiddenBeforeTheWindowIsAttached() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    activity.setContentView(TextView(activity).apply { text = "Private app content" })
    val content = activity.findViewById<View>(android.R.id.content)
    assertFalse(content.isAttachedToWindow)
    ScreenLockController.hideProtectedWindow(activity.window)
    assertEquals(View.INVISIBLE, content.visibility)
    ScreenLockController.revealProtectedWindow(activity.window)
    assertEquals(View.VISIBLE, content.visibility)
  }

  @Test fun systemLockScreenVisibilityDoesNotExemptAppContent() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    activity.setContentView(TextView(activity))
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    ScreenLockController.showWhenLocked(activity.window)
    ScreenLockController.hideProtectedWindow(activity.window)
    assertEquals(View.INVISIBLE, activity.findViewById<View>(android.R.id.content).visibility)
  }

  @Test fun originalHiddenContentIsNotMadeVisibleOnUnlock() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    activity.setContentView(TextView(activity))
    val content = activity.findViewById<View>(android.R.id.content)
    content.visibility = View.GONE
    ScreenLockController.hideProtectedWindow(activity.window)
    ScreenLockController.revealProtectedWindow(activity.window)
    assertEquals(View.GONE, content.visibility)
  }
}
