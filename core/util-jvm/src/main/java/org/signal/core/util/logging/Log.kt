/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.logging

import kotlin.reflect.KClass

object Log {
  private val NOOP_LOGGER: Logger = NoopLogger()
  private var internalCheck: InternalCheck? = null
  private var initializedLogger: Logger = NoopLogger()
  private var activeLogger: Logger = NoopLogger()

  /**
   * @param internalCheck A checker that will indicate if this is an internal user
   * @param enableLogging Flag indicating whether logging should be enabled or not.
   * @param alwaysRedact Flag indicating if messages should be redacted before logging.
   * @param loggers A list of loggers that will be given every log statement.
   */
  @JvmStatic
  fun configure(internalCheck: InternalCheck?, enableLogging: Boolean, alwaysRedact: Boolean, vararg loggers: Logger) {
    Log.internalCheck = internalCheck
    Log.alwaysRedact = alwaysRedact
    initializedLogger = CompoundLogger(loggers.toList())
    setLogging(enableLogging)
  }

  @JvmStatic
  fun initialize(internalCheck: InternalCheck?, vararg loggers: Logger) {
    configure(internalCheck, enableLogging = true, alwaysRedact = false, *loggers)
  }

  @JvmStatic
  fun initialize(vararg loggers: Logger) {
    initialize({ false }, *loggers)
  }

  @JvmStatic
  fun setLogging(enabled: Boolean) {
    activeLogger = if (enabled) initializedLogger else NOOP_LOGGER
  }

  @JvmStatic
  fun wipeLogs() {
    initializedLogger.run {
      flush()
      clear()
    }
  }

  var alwaysRedact: Boolean = false

  private fun redact(message: String?): String? {
    return if (alwaysRedact && !message.isNullOrEmpty()) {
      Scrubber.scrub(message).toString()
    } else {
      message
    }
  }

  @JvmStatic
  fun v(tag: String, message: String) = v(tag, message, null)

  @JvmStatic
  fun v(tag: String, t: Throwable?) = v(tag, null, t)

  @JvmStatic
  fun v(tag: String, message: String?, t: Throwable?) = v(tag, message, t, false)

  @JvmStatic
  fun v(tag: String, message: String?, keepLonger: Boolean) = v(tag, message, null, keepLonger)

  @JvmStatic
  fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = activeLogger.v(tag, redact(message), t, keepLonger)

  @JvmStatic
  fun d(tag: String, message: String) = d(tag, message, null)

  @JvmStatic
  fun d(tag: String, t: Throwable?) = d(tag, null, t)

  @JvmStatic
  fun d(tag: String, message: String?, t: Throwable? = null) = d(tag, message, t, false)

  @JvmStatic
  fun d(tag: String, message: String?, keepLonger: Boolean) = d(tag, message, null, keepLonger)

  @JvmStatic
  fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = activeLogger.d(tag, redact(message), t, keepLonger)

  @JvmStatic
  fun i(tag: String, message: String) = i(tag, message, null)

  @JvmStatic
  fun i(tag: String, t: Throwable?) = i(tag, null, t)

  @JvmStatic
  fun i(tag: String, message: String?, t: Throwable? = null) = i(tag, message, t, false)

  @JvmStatic
  fun i(tag: String, message: String?, keepLonger: Boolean) = i(tag, message, null, keepLonger)

  @JvmStatic
  fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = activeLogger.i(tag, redact(message), t, keepLonger)

  @JvmStatic
  fun w(tag: String, message: String) = w(tag, message, null)

  @JvmStatic
  fun w(tag: String, t: Throwable?) = w(tag, null, t)

  @JvmStatic
  fun w(tag: String, message: String?, t: Throwable? = null) = w(tag, message, t, false)

  @JvmStatic
  fun w(tag: String, message: String?, keepLonger: Boolean) = activeLogger.w(tag, redact(message), keepLonger)

  @JvmStatic
  fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
    activeLogger.w(tag, redact(message), t, keepLonger)
  }

  @JvmStatic
  fun e(tag: String, message: String) = e(tag, message, null)

  @JvmStatic
  fun e(tag: String, t: Throwable?) = e(tag, null, t)

  @JvmStatic
  fun e(tag: String, message: String?, t: Throwable? = null) = e(tag, message, t, false)

  @JvmStatic
  fun e(tag: String, message: String?, keepLonger: Boolean) = e(tag, message, null, keepLonger)

  @JvmStatic
  fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = activeLogger.e(tag, redact(message), t, keepLonger)

  @JvmStatic
  fun tag(clazz: KClass<*>): String {
    return tag(clazz.java)
  }

  @JvmStatic
  fun tag(clazz: Class<*>): String {
    val simpleName = clazz.simpleName

    return if (simpleName.length > 23) {
      simpleName.substring(0, 23)
    } else {
      simpleName
    }
  }

  /**
   * Important: This is not something that can be used to log PII. Instead, it's intended use is for
   * logs that might be too verbose or otherwise unnecessary for public users.
   *
   * @return The normal logger if this is an internal user, or a no-op logger if it isn't.
   */
  @JvmStatic
  fun internal(): Logger {
    return if (internalCheck!!.isInternal()) {
      activeLogger
    } else {
      NOOP_LOGGER
    }
  }

  @JvmStatic
  fun blockUntilAllWritesFinished() {
    activeLogger.flush()
  }

  abstract class Logger {
    abstract fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean)
    abstract fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean)
    abstract fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean)
    abstract fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean)
    abstract fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean)
    abstract fun flush()
    abstract fun clear()

    fun v(tag: String, message: String?) = v(tag, message, null)
    fun v(tag: String, message: String?, t: Throwable?) = v(tag, message, t, false)
    fun v(tag: String, message: String?, keepLonger: Boolean) = v(tag, message, null, keepLonger)

    fun d(tag: String, message: String?) = d(tag, message, null)
    fun d(tag: String, message: String?, t: Throwable?) = d(tag, message, t, false)
    fun d(tag: String, message: String?, keepLonger: Boolean) = d(tag, message, null, keepLonger)

    fun i(tag: String, message: String?) = i(tag, message, null)
    fun i(tag: String, message: String?, t: Throwable?) = i(tag, message, t, false)
    fun i(tag: String, message: String?, keepLonger: Boolean) = i(tag, message, null, keepLonger)

    fun w(tag: String, message: String?) = w(tag, message, null)
    fun w(tag: String, message: String?, t: Throwable?) = w(tag, message, t, false)
    fun w(tag: String, message: String?, keepLonger: Boolean) = w(tag, message, null, keepLonger)

    fun e(tag: String, message: String?) = e(tag, message, null)
    fun e(tag: String, message: String?, t: Throwable?) = e(tag, message, t, false)
    fun e(tag: String, message: String?, keepLonger: Boolean) = e(tag, message, null, keepLonger)
  }

  fun interface InternalCheck {
    fun isInternal(): Boolean
  }
}
