/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import org.junit.rules.ExternalResource
import org.signal.core.util.JsonUtils
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.push.WhoAmIResponse

/**
 * Sets up some common infrastructure for on-device InAppPayment testing
 */
class InAppPaymentsRule : ExternalResource() {
  override fun before() {
    initialiseConfigurationResponse()
    initialisePutSubscription()
    initialiseSetArchiveBackupId()
    initialiseSetAccountAttributes()
    initialiseAccountAndSubscriptionLookups()
  }

  private fun initialiseConfigurationResponse() {
    val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets
    val response = assets.open("inAppPaymentsTests/configuration.json").use { stream ->
      NetworkResult.Success(JsonUtils.fromJson(stream, SubscriptionsConfiguration::class.java))
    }

    AppDependencies.donationsApi.apply {
      every { getDonationsConfiguration(any()) } returns response
    }
  }

  private fun initialisePutSubscription() {
    AppDependencies.donationsApi.apply {
      every { putSubscription(any()) } returns NetworkResult.Success(Unit)
      every { createSubscriber(any(), any()) } returns NetworkResult.Success(Unit)
    }
  }

  private fun initialiseSetArchiveBackupId() {
    AppDependencies.archiveApi.apply {
      every { triggerBackupIdReservation(any(), any(), any()) } returns NetworkResult.Success(Unit)
    }
  }

  private fun initialiseSetAccountAttributes() {
    AppDependencies.accountApi.apply {
      every { setAccountAttributes(any()) } returns NetworkResult.Success(Unit)
    }
  }

  /**
   * Starting the real job loop lets background jobs unrelated to the assertion under test run against the strict
   * API mocks (e.g. [org.thoughtcrime.securesms.jobs.InAppPaymentRecurringContextJob] querying whoAmI, the
   * active subscription, and then submitting receipt credentials). Stub these calls so those jobs hit a handled
   * path and terminate quietly instead of throwing [io.mockk.MockKException] on a job thread, which crashes the
   * whole app process (the test asserts nothing, so it surfaces as "Application crashed" with no failed test).
   * Tests that supply an active subscription push the job past the [getSubscription] guard to
   * [submitReceiptCredentials], so that must be stubbed too. End-to-end coverage of that pipeline is tracked
   * separately; here we only keep the process alive and the logs clean.
   */
  private fun initialiseAccountAndSubscriptionLookups() {
    AppDependencies.accountApi.apply {
      every { whoAmI() } returns NetworkResult.Success(WhoAmIResponse(number = "+15555550123"))
    }

    AppDependencies.donationsApi.apply {
      every { getSubscription(any()) } returns NetworkResult.Success(ActiveSubscription.EMPTY)
      every { submitReceiptCredentials(any(), any()) } returns NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(402))
    }
  }
}
