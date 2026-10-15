package org.thoughtcrime.securesms.screenlock

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.File

enum class ScreenLockMode { OFF, BIOMETRIC, PIN }

/** Kept in noBackupFilesDir: device-bound credentials must never enter a chat backup. */
object ScreenLockPreferences {
  private val TAG = Log.tag(ScreenLockPreferences::class.java)
  private const val MODE_MARKER = "screen-lock-mode"
  internal data class Record(
    val mode: ScreenLockMode,
    val timeoutSeconds: Long = 0,
    val alias: String? = null,
    val salt: String? = null,
    val verifier: String? = null,
    val failures: Int = 0,
    val blockedUntil: Long = 0,
    val bootCount: Int = -1
  )

  private var file: AtomicFile? = null
  private var cached: Record? = null
  val timeouts = ScreenLockTimeouts.seconds

  @JvmStatic fun getMode(context: Context): ScreenLockMode = read(context).mode
  @JvmStatic fun getTimeoutSeconds(context: Context): Long = read(context).timeoutSeconds

  @Synchronized
  internal fun read(context: Context): Record {
    cached?.let { return it }
    val storage = AtomicFile(File(context.noBackupFilesDir, "screen-lock.json"))
    file = storage
    val record = if (storage.baseFile.exists() || File(storage.baseFile.path + ".bak").exists()) {
      try {
        val json = storage.openRead().use { JSONObject(it.bufferedReader().readText()) }
        check(json.getInt("version") == 1)
        Record(
          ScreenLockMode.valueOf(json.getString("mode")),
          ScreenLockTimeouts.migrate(json.getLong("timeout")),
          json.optString("alias").takeIf { it.isNotEmpty() },
          json.optString("salt").takeIf { it.isNotEmpty() },
          json.optString("verifier").takeIf { it.isNotEmpty() },
          json.getInt("failures").also { check(it in 0..30) },
          json.getLong("blockedUntil").also { check(it >= 0) },
          json.getInt("bootCount")
        )
      } catch (_: Exception) {
        // A damaged enabled record must not silently turn authentication off.
        Record(if (modeMarker(context) == ScreenLockMode.BIOMETRIC) ScreenLockMode.BIOMETRIC else ScreenLockMode.PIN)
      }
    } else {
      val marker = modeMarker(context)
      if (marker != null) {
        // An enabled PIN with a missing record remains locked; it is not a first install.
        Record(marker)
      } else {
        val legacy = SecurePreferenceManager.getSecurePreferences(context)
          .getBoolean(TextSecurePreferences.BIOMETRIC_SCREEN_LOCK, false)
        Record(if (legacy) ScreenLockMode.BIOMETRIC else ScreenLockMode.OFF)
      }
    }
    cached = record
    return record
  }

  @Synchronized
  internal fun write(context: Context, record: Record) {
    read(context)
    val json = JSONObject().put("version", 1).put("mode", record.mode.name)
      .put("timeout", record.timeoutSeconds).put("alias", record.alias ?: "")
      .put("salt", record.salt ?: "").put("verifier", record.verifier ?: "")
      .put("failures", record.failures).put("blockedUntil", record.blockedUntil)
      .put("bootCount", record.bootCount)
    val storage = checkNotNull(file)
    val output = storage.startWrite()
    try {
      output.write(json.toString().toByteArray(Charsets.UTF_8))
      storage.finishWrite(output)
      cached = record
      // This non-secret, device-local marker distinguishes record loss from a fresh install.
      // The AtomicFile remains authoritative, including when enabling/disabling is interrupted.
      writeModeMarker(context, record.mode)
    } catch (e: Exception) {
      storage.failWrite(output)
      throw e
    }
  }

  private fun modeMarker(context: Context): ScreenLockMode? {
    val marker = AtomicFile(File(context.noBackupFilesDir, MODE_MARKER))
    if (!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists()) return null
    return try {
      marker.openRead().use { ScreenLockMode.valueOf(it.bufferedReader().readText()) }
    } catch (_: Exception) { ScreenLockMode.PIN }
  }

  private fun writeModeMarker(context: Context, mode: ScreenLockMode) {
    val marker = AtomicFile(File(context.noBackupFilesDir, MODE_MARKER))
    try {
      val output = marker.startWrite()
      try {
        output.write(mode.name.toByteArray(Charsets.UTF_8))
        marker.finishWrite(output)
      } catch (e: Exception) {
        marker.failWrite(output)
        throw e
      }
    } catch (_: Exception) {
      // Do not roll back a committed credential or delete its key on marker failure.
      Log.w(TAG, "Could not persist screen lock presence marker")
    }
  }

  @Synchronized
  internal fun update(context: Context, transform: (Record) -> Record) {
    write(context, transform(read(context)))
  }

  @JvmStatic
  @Synchronized
  fun setTimeoutSeconds(context: Context, seconds: Long) {
    require(seconds in timeouts)
    write(context, read(context).copy(timeoutSeconds = seconds))
  }
}
