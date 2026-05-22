/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class MessageApiV2Test {

  private val authSocket: SignalWebSocket.AuthenticatedWebSocket = mockk()
  private val unauthSocket: SignalWebSocket.UnauthenticatedWebSocket = mockk()
  private val api = MessageApiV2(authSocket, unauthSocket)

  private val request = MessageApiV2.SendMessageRequest(
    messages = listOf(MessageApiV2.Message(type = 1, destinationDeviceId = 1, destinationRegistrationId = 42, content = "abc")),
    timestamp = 1_700_000_000L
  )

  @Test
  fun `200 parses SendMessageResponse and flags sentUnidentified from response`() = runTest {
    stubAuth(status = 200, body = """{"needsSync": true}""", unidentified = true)

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    val success = result as RequestResult.Success<MessageApiV2.SendMessageResponse>
    assertThat(success.result.needsSync).isEqualTo(true)
    assertThat(success.result.sentUnidentified).isEqualTo(true)
  }

  @Test
  fun `401 maps to Unauthorized`() = runTest {
    stubAuth(status = 401)

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    assertNonSuccess(result, MessageApiV2.SendMessageError.Unauthorized)
  }

  @Test
  fun `404 maps to NotRegistered`() = runTest {
    stubAuth(status = 404)

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    assertNonSuccess(result, MessageApiV2.SendMessageError.NotRegistered)
  }

  @Test
  fun `409 parses MismatchedDevices body`() = runTest {
    stubAuth(status = 409, body = """{"missingDevices": [2, 3], "extraDevices": [5]}""")

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val nonSuccess = result as RequestResult.NonSuccess
    val err = nonSuccess.error as MessageApiV2.SendMessageError.MismatchedDevicesError
    assertThat(err.devices.missingDevices).isEqualTo(listOf(2, 3))
    assertThat(err.devices.extraDevices).isEqualTo(listOf(5))
  }

  @Test
  fun `410 parses StaleDevices body`() = runTest {
    stubAuth(status = 410, body = """{"staleDevices": [2]}""")

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val nonSuccess = result as RequestResult.NonSuccess
    val err = nonSuccess.error as MessageApiV2.SendMessageError.StaleDevicesError
    assertThat(err.devices.staleDevices).isEqualTo(listOf(2))
  }

  @Test
  fun `428 parses ProofRequired body and Retry-After header`() = runTest {
    val response: WebsocketResponse = mockk {
      every { status } returns 428
      every { body } returns """{"token": "abc123", "options": ["captcha", "pushChallenge"]}"""
      every { isUnidentified } returns false
      every { getHeader("retry-after") } returns "120"
    }
    coEvery { authSocket.requestSuspend(any<WebSocketRequestMessage>()) } returns response

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val err = (result as RequestResult.NonSuccess).error as MessageApiV2.SendMessageError.ChallengeRequired
    assertThat(err.token).isEqualTo("abc123")
    assertThat(err.options).isEqualTo(listOf("captcha", "pushChallenge"))
    assertThat(err.retryAfter).isEqualTo(120.seconds)
  }

  @Test
  fun `429 with retry-after header maps to RateLimited with Duration`() = runTest {
    val response: WebsocketResponse = mockk {
      every { status } returns 429
      every { body } returns "{}"
      every { isUnidentified } returns false
      every { getHeader("retry-after") } returns "42"
    }
    coEvery { authSocket.requestSuspend(any<WebSocketRequestMessage>()) } returns response

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val err = (result as RequestResult.NonSuccess).error as MessageApiV2.SendMessageError.RateLimited
    assertThat(err.retryAfter).isEqualTo(42.seconds)
  }

  @Test
  fun `429 without retry-after header maps to RateLimited with null Duration`() = runTest {
    val response: WebsocketResponse = mockk {
      every { status } returns 429
      every { body } returns "{}"
      every { isUnidentified } returns false
      every { getHeader("retry-after") } returns null
    }
    coEvery { authSocket.requestSuspend(any<WebSocketRequestMessage>()) } returns response

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val err = (result as RequestResult.NonSuccess).error as MessageApiV2.SendMessageError.RateLimited
    assertThat(err.retryAfter).isEqualTo(null)
  }

  @Test
  fun `508 maps to ServerRejected`() = runTest {
    stubAuth(status = 508)

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    assertNonSuccess(result, MessageApiV2.SendMessageError.ServerRejected)
  }

  @Test
  fun `unexpected status maps to ApplicationError`() = runTest {
    stubAuth(status = 418)

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    assertThat(result).isInstanceOf(RequestResult.ApplicationError::class)
  }

  @Test
  fun `IOException from socket becomes RetryableNetworkError`() = runTest {
    val ioError = IOException("socket closed")
    coEvery { authSocket.requestSuspend(any<WebSocketRequestMessage>()) } throws ioError

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = null, story = false)

    val retry = result as RequestResult.RetryableNetworkError
    assertThat(retry.networkError).isSameInstanceAs(ioError)
  }

  @Test
  fun `sealedSenderAccess routes to unauthenticated socket`() = runTest {
    val sealed: SealedSenderAccess = mockk()
    val response: WebsocketResponse = mockk {
      every { status } returns 200
      every { body } returns """{"needsSync": false}"""
      every { isUnidentified } returns true
    }
    coEvery { unauthSocket.requestSuspend(any(), sealed) } returns response

    val result = api.sendMessage("destination-id", request, sealedSenderAccess = sealed, story = false)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
  }

  private fun stubAuth(status: Int, body: String = "{}", unidentified: Boolean = false) {
    val response: WebsocketResponse = mockk {
      every { this@mockk.status } returns status
      every { this@mockk.body } returns body
      every { isUnidentified } returns unidentified
    }
    coEvery { authSocket.requestSuspend(any<WebSocketRequestMessage>()) } returns response
  }

  private fun assertNonSuccess(result: RequestResult<*, *>, expected: MessageApiV2.SendMessageError) {
    val nonSuccess = result as RequestResult.NonSuccess
    assertThat(nonSuccess.error).isEqualTo(expected)
  }
}
