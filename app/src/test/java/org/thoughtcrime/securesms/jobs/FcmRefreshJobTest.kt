/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.gcm.FcmUtil
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.account.AccountApi

class FcmRefreshJobTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val accountApi = mockk<AccountApi>()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    val signalNetwork = mockk<SignalNetwork>()
    every { signalNetwork.accountApi } returns accountApi
    SignalNetwork.init(signalNetwork)

    mockkStatic(FcmUtil::class)
    every { FcmUtil.deleteFirebaseInstallationId(any()) } just runs
  }

  @After
  fun tearDown() {
    unmockkStatic(FcmUtil::class)
    SignalNetwork.init(SignalNetwork())
  }

  @Test
  fun `when fcm is disabled with a registered token, the server is cleared before local cleanup`() {
    every { signalStore.account.isUnauthorizedReceived } returns false
    every { signalStore.account.fcmEnabled } returns false
    every { signalStore.account.fcmTokenLastSetTime } returns 1
    every { accountApi.clearFcmToken() } returns RequestResult.Success(Unit)

    createJob().onRun()

    verifyOrder {
      accountApi.clearFcmToken()
      signalStore.account.invalidateFcmToken()
      FcmUtil.deleteFirebaseInstallationId(any())
      signalStore.account.fcmToken = null
    }
  }

  private fun createJob(): FcmRefreshJob {
    return FcmRefreshJob().apply { setContext(mockk<Application>(relaxed = true)) }
  }
}
