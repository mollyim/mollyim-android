package org.thoughtcrime.securesms.keyvalue

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_CURRENCY_CODE = "donation.currency.code"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(KEY_CURRENCY_CODE)
}
