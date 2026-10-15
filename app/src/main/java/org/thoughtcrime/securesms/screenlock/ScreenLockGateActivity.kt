package org.thoughtcrime.securesms.screenlock

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.FrameLayout
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.ScreenLockController

/** Protects activities from library modules that cannot inherit the app's BaseActivity. */
// This authentication-only gate must not redirect library activities through app initialization.
@SuppressLint("BaseActivitySubclass")
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
