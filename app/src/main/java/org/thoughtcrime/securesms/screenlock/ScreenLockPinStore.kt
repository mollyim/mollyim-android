package org.thoughtcrime.securesms.screenlock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import org.signal.core.util.crypto.KeyStoreHelper
import org.thoughtcrime.securesms.ScreenLockController
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object ScreenLockPinStore {
  sealed class Result {
    object Success : Result()
    object Incorrect : Result()
    object Unavailable : Result()
    data class Blocked(val milliseconds: Long) : Result()
  }

  private const val PREFIX = "molly-screen-pin-"

  @Synchronized
  fun verify(context: Context, pin: CharArray): Result {
    try {
      var record = ScreenLockPreferences.read(context)
      if (record.mode != ScreenLockMode.PIN) return Result.Unavailable
      val now = SystemClock.elapsedRealtime()
      val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
      if (record.failures >= 5 && record.bootCount != boot) {
        record = record.copy(blockedUntil = now + ScreenLockBackoff.delayMillis(record.failures), bootCount = boot)
        ScreenLockPreferences.update(context) { it.copy(blockedUntil = record.blockedUntil, bootCount = boot) }
      }
      if (record.blockedUntil > now) return Result.Blocked(record.blockedUntil - now)
      val alias = record.alias?.takeIf { it.startsWith(PREFIX) } ?: return Result.Unavailable
      val salt = Base64.decode(record.salt ?: return Result.Unavailable, Base64.NO_WRAP)
      val expected = Base64.decode(record.verifier ?: return Result.Unavailable, Base64.NO_WRAP)
      if (salt.size != 32 || expected.size != 32) return Result.Unavailable
      val actual = ScreenLockPinVerifier.calculate(KeyStoreHelper.getKeyStoreEntryHmac(alias), salt, pin)
      val correct = try { MessageDigest.isEqual(expected, actual) } finally { actual.fill(0) }
      if (correct) {
        ScreenLockPreferences.update(context) { it.copy(failures = 0, blockedUntil = 0, bootCount = boot) }
        return Result.Success
      }
      val failures = (record.failures + 1).coerceAtMost(30)
      val delay = ScreenLockBackoff.delayMillis(failures)
      ScreenLockPreferences.update(context) { it.copy(failures = failures, blockedUntil = now + delay, bootCount = boot) }
      return if (delay > 0) Result.Blocked(delay) else Result.Incorrect
    } catch (_: Exception) {
      return Result.Unavailable
    } catch (_: AssertionError) {
      return Result.Unavailable
    } finally {
      pin.fill('\u0000')
    }
  }

  @Synchronized
  fun install(context: Context, pin: CharArray, generation: Long, active: () -> Boolean): Boolean {
    val alias = PREFIX + UUID.randomUUID()
    var installed = false
    try {
      require(pin.size in 4..16 && pin.all { it in '0'..'9' })
      val key = KeyStoreHelper.createKeyStoreEntryHmac(alias, false)
      val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
      val verifier = ScreenLockPinVerifier.calculate(key, salt, pin)
      val old = ScreenLockPreferences.read(context)
      installed = ScreenLockController.runIfCurrentAuthentication(generation) {
        check(active())
        ScreenLockPreferences.write(context, ScreenLockPreferences.Record(
          mode = ScreenLockMode.PIN,
          timeoutSeconds = ScreenLockPreferences.read(context).timeoutSeconds,
          alias = alias,
          salt = Base64.encodeToString(salt, Base64.NO_WRAP),
          verifier = Base64.encodeToString(verifier, Base64.NO_WRAP)
        ))
        ScreenLockController.configure(context, true)
      }
      verifier.fill(0)
      if (installed) old.alias?.let { deleteKey(it) }
      return installed
    } finally {
      pin.fill('\u0000')
      if (!installed) deleteKey(alias)
    }
  }

  @JvmStatic
  @Synchronized
  fun setMode(context: Context, mode: ScreenLockMode) {
    require(mode != ScreenLockMode.PIN)
    val old = ScreenLockPreferences.read(context)
    ScreenLockPreferences.write(context, ScreenLockPreferences.Record(mode, old.timeoutSeconds))
    old.alias?.let { deleteKey(it) }
  }

  @Synchronized
  fun changeMode(context: Context, mode: ScreenLockMode, generation: Long): Boolean {
    return ScreenLockController.runIfCurrentAuthentication(generation) {
      setMode(context, mode)
      ScreenLockController.configure(context, true)
    }
  }

  private fun deleteKey(alias: String) {
    if (!alias.startsWith(PREFIX)) return
    try {
      KeyStoreHelper.deleteKeyStoreEntry(alias)
    } catch (_: Exception) {
      // An unused key cannot unlock a record.
    } catch (_: AssertionError) {
      // Some KeyStoreHelper failures are wrapped in AssertionError.
    }
  }

}
