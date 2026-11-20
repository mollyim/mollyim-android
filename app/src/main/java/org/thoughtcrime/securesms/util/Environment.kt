package org.thoughtcrime.securesms.util

import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.BuildConfig

object Environment {
  private const val GOOGLE_PLAY_BILLING_APPLICATION_ID = "org.thoughtcrime.securesms"

  const val IS_STAGING: Boolean = BuildConfig.FLAVOR_environment == "staging"
  const val IS_DEV: Boolean = BuildConfig.FLAVOR_environment == "dev"

  @JvmField
  var IS_INSTRUMENTATION: Boolean = false

  fun isInternal(): Boolean {
    return !IS_INSTRUMENTATION && (IS_STAGING || BuildConfig.DEBUG || BuildConfig.FORCE_INTERNAL_USER_FLAG)
  }

  @JvmField
  val IS_LINK_AND_SYNC_AVAILABLE: Boolean = true

  @JvmField
  val PHONENUMBERLESS_REGISTRATION: Boolean = true

  /** Whether this build is distributed through the Play Store, and so can use Google Play billing at all. */
  @JvmStatic
  fun supportsGooglePlayBilling(): Boolean {
    return BuildConfig.APPLICATION_ID == GOOGLE_PLAY_BILLING_APPLICATION_ID
  }

  object Backups {
    @JvmStatic
    fun supportsGooglePlayBilling(): Boolean {
      return Environment.supportsGooglePlayBilling()
    }

    @JvmStatic
    fun isNewFormatSupportedForLocalBackup(): Boolean = true
  }

  object Donations {
    @JvmStatic
    @get:JvmName("getStripeConfiguration")
    val STRIPE_CONFIGURATION = StripeApi.Configuration(
      baseUrl = BuildConfig.STRIPE_BASE_URL,
      publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
    )
  }

  object Calling {
    @JvmStatic
    fun defaultSfuUrl(): String {
      return if (IS_STAGING) BuildConfig.SIGNAL_STAGING_SFU_URL else BuildConfig.SIGNAL_SFU_URL
    }
  }
}
