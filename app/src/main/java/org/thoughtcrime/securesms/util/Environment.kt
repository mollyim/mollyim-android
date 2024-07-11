package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.BuildConfig

@Suppress("KotlinConstantConditions")
object Environment {
  const val IS_STAGING: Boolean = BuildConfig.FLAVOR_environment == "staging"
  const val IS_DEV: Boolean = BuildConfig.FLAVOR_environment == "dev" || BuildConfig.BUILD_TYPE == "instrumentation"

  object Calling {
    @JvmStatic
    fun defaultSfuUrl(): String {
      return if (IS_STAGING) BuildConfig.SIGNAL_STAGING_SFU_URL else BuildConfig.SIGNAL_SFU_URL
    }
  }
}
