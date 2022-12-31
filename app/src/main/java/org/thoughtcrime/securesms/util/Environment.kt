package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.BuildConfig

object Environment {
  const val IS_STAGING: Boolean = BuildConfig.BUILD_ENVIRONMENT_TYPE == "Staging"
  const val IS_DEV: Boolean = BuildConfig.BUILD_VARIANT_TYPE == "Instrumentation"

  object Calling {
    @JvmStatic
    fun defaultSfuUrl(): String {
      return if (IS_STAGING) BuildConfig.SIGNAL_STAGING_SFU_URL else BuildConfig.SIGNAL_SFU_URL
    }
  }
}

object Release {
  const val IS_DEBUGGABLE: Boolean = BuildConfig.BUILD_VARIANT_TYPE == "Debug"
  const val IS_FOSS: Boolean = BuildConfig.FLAVOR_distribution == "free"
}
