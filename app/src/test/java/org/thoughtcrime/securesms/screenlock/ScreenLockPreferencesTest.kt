package org.thoughtcrime.securesms.screenlock

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ScreenLockPreferencesTest {
  private lateinit var context: Context

  @Before fun setUp() {
    clearCache()
    context = RuntimeEnvironment.getApplication()
    File(context.noBackupFilesDir, "screen-lock-mode").writeText("OFF")
  }

  @After fun tearDown() { clearCache() }

  private fun clearCache() {
    for (name in listOf("cached", "file")) {
      ScreenLockPreferences::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }
  }

  private fun recordFile() = File(context.noBackupFilesDir, "screen-lock.json")

  @Test fun missingPinRecordDoesNotBecomeAnUnlockedFreshInstall() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.PIN))
    assertTrue(recordFile().delete())
    clearCache()
    assertEquals(ScreenLockMode.PIN, ScreenLockPreferences.getMode(context))
    assertNull(ScreenLockPreferences.read(context).verifier)
  }

  @Test fun missingBiometricRecordKeepsItsAuthenticationMethod() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.BIOMETRIC))
    assertTrue(recordFile().delete())
    clearCache()
    assertEquals(ScreenLockMode.BIOMETRIC, ScreenLockPreferences.getMode(context))
  }

  @Test fun explicitDisableDoesNotResurrectAnOldPinMarker() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.PIN))
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.OFF))
    assertTrue(recordFile().delete())
    clearCache()
    assertEquals(ScreenLockMode.OFF, ScreenLockPreferences.getMode(context))
  }

  @Test fun damagedPinRecordRemainsProtected() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.PIN))
    recordFile().writeText("{broken")
    clearCache()
    assertEquals(ScreenLockMode.PIN, ScreenLockPreferences.getMode(context))
  }

  @Test fun authoritativeRecordWinsOverAnOlderPresenceMarker() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.PIN))
    File(context.noBackupFilesDir, "screen-lock-mode").writeText("OFF")
    clearCache()
    assertEquals(ScreenLockMode.PIN, ScreenLockPreferences.getMode(context))
  }

  @Test fun damagedBiometricRecordKeepsBiometricAuthentication() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.BIOMETRIC))
    recordFile().writeText("{broken")
    clearCache()
    assertEquals(ScreenLockMode.BIOMETRIC, ScreenLockPreferences.getMode(context))
  }

  @Test fun unreadablePresenceMarkerDoesNotDisableTheLock() {
    File(context.noBackupFilesDir, "screen-lock-mode").writeText("unknown-mode")
    clearCache()
    assertEquals(ScreenLockMode.PIN, ScreenLockPreferences.getMode(context))
  }

  @Test fun legacyDelayIsMigratedWhenReadingAnActualRecord() {
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(ScreenLockMode.BIOMETRIC, 30))
    val json = org.json.JSONObject(recordFile().readText()).put("timeout", -1)
    recordFile().writeText(json.toString())
    clearCache()
    assertEquals(0L, ScreenLockPreferences.getTimeoutSeconds(context))
    assertEquals(ScreenLockMode.BIOMETRIC, ScreenLockPreferences.getMode(context))
  }
}
