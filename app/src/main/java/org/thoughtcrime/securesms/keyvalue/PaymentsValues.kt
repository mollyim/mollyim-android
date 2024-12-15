package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.payments.Entropy

class PaymentsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(PaymentsValues::class.java)

    private const val MOB_PAYMENTS_ENABLED = "mob_payments_enabled"
    private const val PAYMENTS_ENTROPY = "payments_entropy"
    private const val MOB_LEDGER = "mob_ledger"
    private const val PAYMENTS_CURRENT_CURRENCY = "payments_current_currency"
    private const val DEFAULT_CURRENCY_CODE = "GBP"
    private const val USER_CONFIRMED_MNEMONIC = "mob_payments_user_confirmed_mnemonic"
    private const val USER_CONFIRMED_MNEMONIC_LARGE_BALANCE = "mob_payments_user_confirmed_mnemonic_large_balance"
    private const val SHOW_ABOUT_MOBILE_COIN_INFO_CARD = "mob_payments_show_about_mobile_coin_info_card"
    private const val SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD = "mob_payments_show_adding_to_your_wallet_info_card"
    private const val SHOW_CASHING_OUT_INFO_CARD = "mob_payments_show_cashing_out_info_card"
    private const val SHOW_RECOVERY_PHRASE_INFO_CARD = "mob_payments_show_recovery_phrase_info_card"
    private const val SHOW_UPDATE_PIN_INFO_CARD = "mob_payments_show_update_pin_info_card"
    private const val PAYMENT_LOCK_ENABLED = "mob_payments_payment_lock_enabled"
    private const val PAYMENT_LOCK_TIMESTAMP = "mob_payments_payment_lock_timestamp"
    private const val PAYMENT_LOCK_SKIP_COUNT = "mob_payments_payment_lock_skip_count"
    private const val SHOW_SAVE_RECOVERY_PHRASE = "mob_show_save_recovery_phrase"
  }

  public override fun onFirstEverAppLaunch() {}

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      PAYMENTS_ENTROPY,
      MOB_PAYMENTS_ENABLED,
      MOB_LEDGER,
      PAYMENTS_CURRENT_CURRENCY,
      DEFAULT_CURRENCY_CODE,
      USER_CONFIRMED_MNEMONIC,
      USER_CONFIRMED_MNEMONIC_LARGE_BALANCE,
      SHOW_ABOUT_MOBILE_COIN_INFO_CARD,
      SHOW_ADDING_TO_YOUR_WALLET_INFO_CARD,
      SHOW_CASHING_OUT_INFO_CARD,
      SHOW_RECOVERY_PHRASE_INFO_CARD,
      SHOW_UPDATE_PIN_INFO_CARD,
      PAYMENT_LOCK_ENABLED,
      PAYMENT_LOCK_TIMESTAMP,
      PAYMENT_LOCK_SKIP_COUNT,
      SHOW_SAVE_RECOVERY_PHRASE
    )
  }

  /**
   * Consider using [.getPaymentsAvailability] which includes feature flag and region status.
   */
  fun mobileCoinPaymentsEnabled(): Boolean {
    return getBoolean(MOB_PAYMENTS_ENABLED, false)
  }

  /**
   * Returns the local payments entropy, regardless of whether payments is currently enabled.
   *
   *
   * And null if has never been set.
   */
  val paymentsEntropy: Entropy?
    get() = Entropy.fromBytes(store.getBlob(PAYMENTS_ENTROPY, null))

  /**
   * Does not trigger a storage sync.
   */
  fun setEnabledAndEntropy(enabled: Boolean, entropy: Entropy?) {
    val writer = store.beginWrite()

    if (entropy != null) {
      writer.putBlob(PAYMENTS_ENTROPY, entropy.bytes)
    }

    writer.putBoolean(MOB_PAYMENTS_ENABLED, enabled).commit()
  }
}
