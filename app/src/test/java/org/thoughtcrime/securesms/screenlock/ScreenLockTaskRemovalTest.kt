package org.thoughtcrime.securesms.screenlock

import android.app.Application
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.DummyActivity
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.service.KeyCachingService
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ScreenLockTaskRemovalTest {
  private lateinit var application: Application

  @Before fun setUp() {
    clearPreferenceCache()
    application = RuntimeEnvironment.getApplication()
    File(application.noBackupFilesDir, "screen-lock-mode").writeText("OFF")
  }

  @After fun tearDown() {
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.OFF))
    ScreenLockController.configure(application, false)
    clearPreferenceCache()
  }

  private fun clearPreferenceCache() {
    for (name in listOf("cached", "file")) {
      ScreenLockPreferences::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }
  }

  @Test fun serviceTaskRemovalEndsBothPinAndBiometricSessions() {
    val service = Robolectric.buildService(KeyCachingService::class.java).get()
    for (mode in listOf(ScreenLockMode.PIN, ScreenLockMode.BIOMETRIC)) {
      ScreenLockPreferences.write(application, ScreenLockPreferences.Record(mode, 600))
      ScreenLockController.configure(application, true)
      val token = ScreenLockController.authenticationGeneration()
      assertTrue(ScreenLockController.isAccessAllowed())

      service.onTaskRemoved(Intent())

      assertFalse(ScreenLockController.isAccessAllowed())
      assertFalse(ScreenLockController.completeAuthentication(token))
      ScreenLockController.onActivityResuming()
      assertFalse(ScreenLockController.isAccessAllowed())
      assertEquals(DummyActivity::class.java.name, shadowOf(application).nextStartedActivity.component!!.className)
    }
  }

  @Test fun serviceTaskRemovalKeepsDisabledScreenLockDisabled() {
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.OFF))
    ScreenLockController.configure(application, true)
    val service = Robolectric.buildService(KeyCachingService::class.java).get()

    service.onTaskRemoved(Intent())

    assertTrue(ScreenLockController.isAccessAllowed())
  }
}
