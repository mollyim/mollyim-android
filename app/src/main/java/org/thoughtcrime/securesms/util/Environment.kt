package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.BuildConfig

object Environment {
  const val IS_STAGING: Boolean = BuildConfig.BUILD_ENVIRONMENT_TYPE == "Staging"
}

object Release {
  const val IS_DEBUGGABLE: Boolean = BuildConfig.BUILD_VARIANT_TYPE == "Debug"
  const val IS_INSIDER: Boolean = BuildConfig.APPLICATION_ID == "im.molly.insider"
}
