package org.thoughtcrime.securesms.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.LogRecorder

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class RemoteConfigInitializationTest {

  private var wasUnderTest = false

  @Before
  fun setUp() {
    wasUnderTest = RemoteConfig.underTest

    SignalStore.testInject(null)
    resetRemoteConfig()
    RemoteConfig.underTest = true
    Log.initialize(LogRecorder())
  }

  @After
  fun tearDown() {
    resetRemoteConfig()
    SignalStore.testInject(null)
    RemoteConfig.underTest = wasUnderTest
  }

  @Test
  fun `internal user falls back without initializing an unavailable store`() {
    assertFalse(SignalStore.isInitialized)

    assertFalse(RemoteConfig.internalUser)

    assertFalse(RemoteConfig.initialized)
    assertFalse(SignalStore.isInitialized)
  }

  @Test
  fun `flags use their declared defaults when the store is unavailable`() {
    assertEquals(4, RemoteConfig.pinnedChatLimit)
    assertTrue(RemoteConfig.retryReceipts)
    assertTrue(RemoteConfig.libsignalConfigs.isEmpty())
    assertTrue(RemoteConfig.debugDiskValues.isEmpty())
    assertTrue(RemoteConfig.debugPendingDiskValues.isEmpty())
    assertFalse(RemoteConfig.initialized)
  }

  @Test
  fun `early default does not prevent later initialization`() {
    assertFalse(RemoteConfig.internalUser)

    installSignalStore()
    setPendingConfig("android.internalUser" to true)
    RemoteConfig.init()

    assertTrue(RemoteConfig.initialized)
    assertTrue(RemoteConfig.internalUser)
  }

  @Test
  fun `available store retains lazy initialization`() {
    installSignalStore()
    setPendingConfig("global.pinnedChatLimit" to "9")

    assertEquals(9, RemoteConfig.pinnedChatLimit)
    assertTrue(RemoteConfig.initialized)
  }

  private fun installSignalStore() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val store = KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())
    SignalStore.testInject(SignalStore(application, store))
  }

  private fun setPendingConfig(vararg values: Pair<String, Any>) {
    val json = JSONObject()
    values.forEach { (key, value) -> json.put(key, value) }
    SignalStore.remoteConfig.pendingConfig = json.toString()
  }

  private fun resetRemoteConfig() {
    RemoteConfig.overrides = emptyMap()
    RemoteConfig.REMOTE_VALUES.clear()
    RemoteConfig.initialized = false
    RemoteConfig.underTest = false
    RemoteConfig.internalUserDisabled = false
  }
}
