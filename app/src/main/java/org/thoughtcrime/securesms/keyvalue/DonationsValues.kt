package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.subscription.Subscriber
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(DonationsValues::class.java)

    private const val KEY_SUBSCRIPTION_CURRENCY_CODE = "donation.currency.code"
    private const val KEY_CURRENCY_CODE_BOOST = "donation.currency.code.boost"
    private const val KEY_SUBSCRIBER_ID_PREFIX = "donation.subscriber.id."
    private const val KEY_LAST_KEEP_ALIVE_LAUNCH = "donation.last.successful.ping"
    private const val KEY_LAST_END_OF_PERIOD_SECONDS = "donation.last.end.of.period"
    private const val USER_MANUALLY_CANCELLED = "donation.user.manually.cancelled"
    private const val DISPLAY_BADGES_ON_PROFILE = "donation.display.badges.on.profile"
    private const val SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT = "donation.should.cancel.subscription.before.next.subscribe.attempt"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(
    KEY_CURRENCY_CODE_BOOST,
    KEY_LAST_KEEP_ALIVE_LAUNCH,
    KEY_LAST_END_OF_PERIOD_SECONDS,
    SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT
  )

  private fun getSubscriptionCurrency(): Currency {
    val currencyCode = getString(KEY_SUBSCRIPTION_CURRENCY_CODE, null)
    val currency: Currency? = if (currencyCode == null) {
      null
    } else {
      CurrencyUtil.getCurrencyByCurrencyCode(currencyCode)
    }

    return currency ?: Currency.getInstance("USD")
  }

  private fun getSubscriber(currency: Currency): Subscriber? {
    val currencyCode = currency.currencyCode
    val subscriberIdBytes = getBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", null)

    return if (subscriberIdBytes == null) {
      null
    } else {
      Subscriber(SubscriberId.fromBytes(subscriberIdBytes), currencyCode)
    }
  }

  // MOLLY: Functions below are only required for storage sync

  fun getSubscriberStorageSync(): Subscriber? {
    return getSubscriber(getSubscriptionCurrency())
  }

  fun setSubscriberStorageSync(subscriber: Subscriber) {
    val currencyCode = subscriber.currencyCode
    store.beginWrite()
      .putBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", subscriber.subscriberId.bytes)
      .putString(KEY_SUBSCRIPTION_CURRENCY_CODE, currencyCode)
      .apply()
  }

  fun setDisplayBadgesOnProfileStorageSync(enabled: Boolean) {
    putBoolean(DISPLAY_BADGES_ON_PROFILE, enabled)
  }

  fun getDisplayBadgesOnProfileStorageSync(): Boolean {
    return getBoolean(DISPLAY_BADGES_ON_PROFILE, false)
  }

  fun isUserManuallyCancelledSync(): Boolean {
    return getBoolean(USER_MANUALLY_CANCELLED, false)
  }

  fun markUserManuallyCancelledSync() {
    putBoolean(USER_MANUALLY_CANCELLED, true)
  }

  fun clearUserManuallyCancelledSync() {
    remove(USER_MANUALLY_CANCELLED)
  }
}
