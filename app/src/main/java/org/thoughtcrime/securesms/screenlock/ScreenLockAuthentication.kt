package org.thoughtcrime.securesms.screenlock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment

object ScreenLockAuthentication {
  @JvmStatic
  fun authenticate(activity: FragmentActivity, listener: BiometricDialogFragment.Listener, fullScreen: Boolean) {
    if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved) {
      listener.onCancel(false)
      return
    }
    val generation = ScreenLockController.authenticationGeneration()
    val guarded = object : BiometricDialogFragment.Listener {
      override fun onSuccess(): Boolean =
        if (!activity.isFinishing && !activity.isDestroyed && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ScreenLockController.isCurrentAuthentication(generation)) listener.onSuccess()
        else { listener.onCancel(false); true }

      override fun onError(message: CharSequence): Boolean = listener.onError(message)
      override fun onCancel(fromUser: Boolean): Boolean = listener.onCancel(fromUser)
      override fun onNotEnrolled(message: CharSequence): Boolean = listener.onNotEnrolled(message)
    }
    when (ScreenLockPreferences.getMode(activity)) {
      ScreenLockMode.PIN -> ScreenLockPinDialogFragment.show(activity, false, guarded)
      ScreenLockMode.BIOMETRIC -> BiometricDialogFragment.findOrAddFragment(activity.supportFragmentManager, fullScreen).showPrompt(activity, guarded)
      ScreenLockMode.OFF -> guarded.onSuccess()
    }
  }
}
