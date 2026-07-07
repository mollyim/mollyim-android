/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wallet.PaymentData
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.rules.ExternalResource
import org.signal.donations.GooglePayApi
import org.signal.donations.InAppPaymentType
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError
import org.thoughtcrime.securesms.SignalInstrumentationApplicationContext
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorTestTags
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Makes Google Pay appear available and return a fake [com.google.android.gms.wallet.PaymentData] without
 * launching the real Google Pay sheet, allowing checkout to be driven to the payment pipeline in instrumentation.
 */
class GooglePayTestRule : ExternalResource() {
  override fun before() {
    val paymentData = mockk<PaymentData> {
      every { toJson() } returns GOOGLE_PAY_PAYMENT_DATA_JSON
    }

    mockkConstructor(GooglePayApi::class)
    every { anyConstructed<GooglePayApi>().queryIsReadyToPay() } returns Completable.complete()
    every { anyConstructed<GooglePayApi>().requestPayment(any(), any(), any()) } just Runs
    every { anyConstructed<GooglePayApi>().onActivityResult(any(), any(), any(), any(), any()) } answers {
      arg<GooglePayApi.PaymentRequestCallback>(4).onSuccess(paymentData)
    }
  }

  override fun after() {
    unmockkConstructor(GooglePayApi::class)
  }
}

/**
 * Minimal but well-formed Google Pay payload. [org.signal.donations.GooglePayPaymentSource] parses
 * `paymentMethodData.tokenizationData.token` (itself a JSON object with an `id`) when the source is
 * serialized into the setup job, so a relaxed mock that returns an empty body fails before the job runs.
 */
private const val GOOGLE_PAY_PAYMENT_DATA_JSON = """{"paymentMethodData":{"tokenizationData":{"token":"{\"id\":\"tok_test\"}"}},"email":"test@signal.org"}"""

/**
 * Forces real donation-permit acquisition to fail at the issuer, exercising the permit code path through
 * [org.thoughtcrime.securesms.components.settings.app.subscription.permits.DonationPermits].
 */
fun failDonationPermitAcquisition(statusCode: Int = 500) {
  AppDependencies.donationPermitsRepository.clearPermits()
  every { AppDependencies.donationsApi.createDonationPermits(any()) } returns RequestResult.NonSuccess(RestStatusCodeError(statusCode, emptyMap(), null))
}

/**
 * The instrumentation app stubs [org.thoughtcrime.securesms.ApplicationContext.beginJobLoop] to a no-op, so enqueued
 * jobs never run. The checkout pipeline drives a real setup job through the JobManager, so the loop must be started.
 */
fun startJobLoopForTests() {
  (AppDependencies.application as SignalInstrumentationApplicationContext).beginJobLoopForTests()
}

/**
 * Selects Google Pay in the Compose gateway selector and feeds the stubbed Google Pay result back into the
 * checkout, navigating to the payment-in-progress screen where the pipeline runs.
 */
fun ActivityScenario<CheckoutFlowActivity>.selectGooglePay(
  composeRule: ComposeTestRule,
  scheduler: TestScheduler,
  inAppPaymentType: InAppPaymentType
) {
  val deadline = SystemClock.uptimeMillis() + 10_000
  var present = false
  while (SystemClock.uptimeMillis() < deadline && !present) {
    scheduler.triggerActions()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    present = try {
      composeRule.onAllNodesWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
    } catch (e: IllegalStateException) {
      false
    }
    if (!present) {
      Thread.sleep(100)
    }
  }
  check(present) { "Google Pay button never appeared in the gateway selector." }

  composeRule.onNodeWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).performClick()
  pump(scheduler, iterations = 20)

  onActivity { activity ->
    (activity as GooglePayComponent).googlePayResultPublisher.onNext(
      GooglePayComponent.GooglePayResult(
        requestCode = InAppPaymentsRepository.getGooglePayRequestCode(inAppPaymentType),
        resultCode = Activity.RESULT_OK,
        data = Intent()
      )
    )
  }
}

/**
 * Advances the Rx [scheduler] and pumps the main looper until the checkout error dialog with [titleResId] is
 * displayed, bridging the real JobManager-backed setup job, the Rx observers, and dialog rendering.
 */
fun awaitDonationErrorDialog(scheduler: TestScheduler, titleResId: Int, timeoutMillis: Long = 15_000) {
  val deadline = SystemClock.uptimeMillis() + timeoutMillis
  var lastFailure: Throwable? = null

  while (SystemClock.uptimeMillis() < deadline) {
    scheduler.triggerActions()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    try {
      onView(withText(titleResId)).inRoot(isDialog()).check(matches(isDisplayed()))
      return
    } catch (t: Throwable) {
      lastFailure = t
    }

    Thread.sleep(100)
  }

  throw AssertionError("Donation error dialog ($titleResId) was not displayed within ${timeoutMillis}ms.", lastFailure)
}

private fun pump(scheduler: TestScheduler, iterations: Int, intervalMs: Long = 100) {
  repeat(iterations) {
    scheduler.triggerActions()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    Thread.sleep(intervalMs)
  }
}
