package org.signal.core.util.logging

import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import java.io.BufferedReader
import java.io.InputStreamReader

@SuppressLint("LogNotSignal")
object AndroidLogger : Log.Logger() {
  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    android.util.Log.v(tag, message, t)
  }

  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    android.util.Log.d(tag, message, t)
  }

  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    android.util.Log.i(tag, message, t)
  }

  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    android.util.Log.w(tag, message, t)
  }

  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    android.util.Log.e(tag, message, t)
  }

  override fun flush() = Unit

  override fun clear() {
    runCatching {
      Runtime.getRuntime().exec("logcat -c")
    }
  }

  @JvmStatic
  @get:WorkerThread
  val logcatDump: CharSequence?
    get() = runCatching {
      val process = Runtime.getRuntime().exec("logcat -d")
      val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
      val log = StringBuilder()
      val separator = System.lineSeparator()
      do {
        val line = bufferedReader.readLine() ?: break
        log.append(line)
        log.append(separator)
      } while (true)
      log
    }.getOrNull()
}
