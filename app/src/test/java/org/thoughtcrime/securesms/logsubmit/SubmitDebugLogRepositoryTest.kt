package org.thoughtcrime.securesms.logsubmit

import android.app.Application
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class SubmitDebugLogRepositoryTest {

  private var wasUnderTest = false

  @Before
  fun setUp() {
    wasUnderTest = RemoteConfig.underTest

    SignalStore.testInject(null)
    RemoteConfig.initialized = false
    RemoteConfig.underTest = true
    Log.initialize(LogRecorder())
  }

  @After
  fun tearDown() {
    SignalStore.testInject(null)
    RemoteConfig.initialized = false
    RemoteConfig.underTest = wasUnderTest
  }

  @Test
  fun `section initialization does not require SignalStore while locked`() {
    assertTrue(KeyCachingService.isLocked())

    val sectionsField = SubmitDebugLogRepository::class.java.getDeclaredField("SECTIONS")
    sectionsField.isAccessible = true

    val sections = sectionsField.get(null) as List<*>

    assertTrue(sections.isNotEmpty())
    assertFalse(RemoteConfig.initialized)
  }
}
