/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.app.base

import android.app.Application
import androidx.annotation.VisibleForTesting

/**
 * Process-wide holder for the current [Application].
 *
 * Used for code that needs an [Application] before AppDependencies is initialized and for
 * unit tests that do not run with [org.thoughtcrime.securesms.ApplicationContext].
 */
object ApplicationInstance {

  private var instance: Application? = null

  @JvmStatic
  fun set(application: Application) {
    instance = application
  }

  @JvmStatic
  fun get(): Application = checkNotNull(instance)

  @JvmStatic
  fun getOrNull(): Application? = instance

  @VisibleForTesting
  fun setForTests(application: Application) {
    instance = application
  }

  @VisibleForTesting
  fun clearForTests() {
    instance = null
  }
}
