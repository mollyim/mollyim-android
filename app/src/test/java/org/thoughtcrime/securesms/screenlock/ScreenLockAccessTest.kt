package org.thoughtcrime.securesms.screenlock

import android.app.Application
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.service.KeyCachingService
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ScreenLockAccessTest {
  private lateinit var application: Application
  private lateinit var visibility: ScreenLockVisibilityObserver

  @Before fun setUp() {
    clearPreferenceCache()
    application = RuntimeEnvironment.getApplication()
    File(application.noBackupFilesDir, "screen-lock-mode").writeText("OFF")
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.PIN, 600))
    KeyCachingService.clearMasterSecret()
    visibility = mockk()
    every { visibility.authenticationChanged() } just Runs
    every { visibility.checkTaskRemoval() } just Runs
    setVisibility(visibility)
    ScreenLockController.configure(application, true)
    ScreenLockController.onActivitiesHidden(application)
    assertTrue(ScreenLockController.isAccessAllowed())
  }

  @After fun tearDown() {
    setVisibility(null)
    ScreenLockPreferences.write(application, ScreenLockPreferences.Record(ScreenLockMode.OFF))
    ScreenLockController.configure(application, false)
    clearPreferenceCache()
  }

  private fun setVisibility(observer: ScreenLockVisibilityObserver?) {
    ScreenLockController::class.java.getDeclaredField("visibility").apply { isAccessible = true }.set(null, observer)
  }

  private fun clearPreferenceCache() {
    for (name in listOf("cached", "file")) {
      ScreenLockPreferences::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }
  }

  private fun removeTaskWithoutAServiceCallback() {
    every { visibility.checkTaskRemoval() } answers { ScreenLockController.onTaskRemoved(application) }
  }

  @Test fun accessPredicateDetectsRemovalBeforeAnotherActivityStarts() {
    removeTaskWithoutAServiceCallback()
    assertFalse(ScreenLockController.isAccessAllowed())
  }

  @Test fun aQueuedWorkerActionCannotUseTheRemovedTasksGracePeriod() {
    val token = ScreenLockController.authenticationGeneration()
    val ran = AtomicBoolean(false)
    removeTaskWithoutAServiceCallback()
    val worker = Executors.newSingleThreadExecutor()
    try {
      val allowed = worker.submit(Callable {
        ScreenLockController.runIfAccessAllowed(token, Runnable { ran.set(true) })
      }).get(5, TimeUnit.SECONDS)
      assertFalse(allowed)
      assertFalse(ran.get())
    } finally {
      worker.shutdownNow()
    }
  }

  @Test fun returningWorkerActionUsesItsFallbackAfterTaskRemoval() {
    val token = ScreenLockController.authenticationGeneration()
    val ran = AtomicBoolean(false)
    removeTaskWithoutAServiceCallback()

    val result = ScreenLockController.withAccessAllowed(token, "blocked") {
      ran.set(true)
      "private data"
    }

    assertEquals("blocked", result)
    assertFalse(ran.get())
  }
}
