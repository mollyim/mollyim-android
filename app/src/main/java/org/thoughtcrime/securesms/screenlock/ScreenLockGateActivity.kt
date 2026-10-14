package org.thoughtcrime.securesms.screenlock

import android.os.Bundle
import android.widget.FrameLayout
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.ScreenLockController

/** Protects activities from library modules that cannot inherit the app's BaseActivity. */
class ScreenLockGateActivity : BaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(FrameLayout(this))
  }

  override fun onPostResume(screenLocked: Boolean) {
    super.onPostResume(screenLocked)
    if (ScreenLockController.isAccessAllowed()) finish()
  }

  override fun onAuthenticationCancel() {
    moveTaskToBack(true)
    finish()
  }
}
