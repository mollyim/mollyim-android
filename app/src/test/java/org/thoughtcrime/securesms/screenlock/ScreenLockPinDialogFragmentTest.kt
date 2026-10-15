package org.thoughtcrime.securesms.screenlock

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputEditText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ScreenLockPinDialogFragmentTest {
  class HostActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) {
      setTheme(R.style.Theme_Signal_DayNight_NoActionBar)
      super.onCreate(state)
    }
  }

  @Before fun setUp() {
    clearPreferenceCache()
    val application = RuntimeEnvironment.getApplication()
    File(application.noBackupFilesDir, "screen-lock-mode").writeText("OFF")
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.PIN))
    ScreenLockController.configure(application, false)
  }

  @After fun tearDown() {
    val application = RuntimeEnvironment.getApplication()
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.OFF))
    ScreenLockController.configure(application, false)
    clearPreferenceCache()
  }

  private fun clearPreferenceCache() {
    for (name in listOf("cached", "file")) {
      ScreenLockPreferences::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }
  }

  private fun show(activity: HostActivity, setup: Boolean = false): ScreenLockPinDialogFragment {
    ScreenLockPinDialogFragment.show(activity, setup, object : BiometricDialogFragment.Listener {
      override fun onSuccess() = true
      override fun onCancel(fromUser: Boolean) = true
      override fun onError(message: CharSequence) = true
      override fun onNotEnrolled(message: CharSequence) = true
    })
    return activity.supportFragmentManager.fragments.filterIsInstance<ScreenLockPinDialogFragment>().single()
  }

  @Test fun pinDialogFocusesTheInputAndAllowsTheKeyboardOnFirstDisplay() {
    val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
    val fragment = show(controller.get())
    val input = fragment.requireView().findViewById<TextInputEditText>(R.id.screen_lock_pin_input)
    val attributes = fragment.requireDialog().window!!.attributes

    assertTrue(input.hasFocus())
    assertEquals(0, attributes.flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM))
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE, attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
    controller.pause().stop().destroy()
  }

  @Test fun returningToThePinDialogRestoresInputFocusAndClearsOldDigits() {
    ScreenLockController.configure(RuntimeEnvironment.getApplication(), true)
    val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
    val fragment = show(controller.get(), setup = true)
    val input = fragment.requireView().findViewById<TextInputEditText>(R.id.screen_lock_pin_input)
    input.setText("1234")
    controller.pause()
    assertTrue(fragment.requireView().findViewById<android.view.View>(R.id.screen_lock_pin_cancel).requestFocus())
    controller.resume().postResume()

    assertTrue(input.hasFocus())
    assertEquals("", input.text.toString())
    controller.pause().stop().destroy()
  }
}
