/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import arrow.core.getOrElse
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import kotlin.time.Duration

/**
 * Collection of message-related endpoints.
 */
class MessageApiV2(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {
  /**
   * Sends a message to a single recipient. Uses the unauthenticated websocket if [sealedSenderAccess] is provided,
   * and the authenticated websocket otherwise.
   *
   * PUT /v1/messages/[destination]?story=[story]
   * - 200: Success
   * - 401: Authorization or [sealedSenderAccess] is missing or incorrect
   * - 404: Recipient is not a registered Signal user
   * - 409: Mismatched devices for the recipient
   * - 410: Stale devices for some recipient devices
   * - 428: Sender must complete a challenge before proceeding
   * - 508: Server rejected the message
   */
  suspend fun sendMessage(
    destination: String,
    messageList: SendMessageRequest,
    sealedSenderAccess: SealedSenderAccess?,
    story: Boolean
  ): RequestResult<SendMessageResponse, SendMessageError> {
    val requestBody = SignalJson.encode(SendMessageRequest.serializer(), messageList).getOrElse { return RequestResult.ApplicationError(it.cause) }
    val request = WebSocketRequestMessage.put("/v1/messages/$destination?story=$story", requestBody)

    return try {
      val response = if (sealedSenderAccess == null) {
        authWebSocket.requestSuspend(request)
      } else {
        unauthWebSocket.requestSuspend(request, sealedSenderAccess)
      }

      when (response.status) {
        200 -> {
          SignalJson
            .decode(SendMessageResponse.serializer(), response.body)
            .map { it.copy(sentUnidentified = response.isUnidentified) }
            .fold(
              ifLeft = { RequestResult.ApplicationError(it.cause) },
              ifRight = { RequestResult.Success(it) }
            )
        }
        401 -> {
          RequestResult.NonSuccess(SendMessageError.Unauthorized)
        }
        404 -> {
          RequestResult.NonSuccess(SendMessageError.NotRegistered)
        }
        409 -> {
          SignalJson
            .decode(MismatchedDevices.serializer(), response.body)
            .fold(
              ifLeft = { RequestResult.ApplicationError(it.cause) },
              ifRight = { RequestResult.NonSuccess(SendMessageError.MismatchedDevicesError(it)) }
            )
        }
        410 -> {
          SignalJson
            .decode(StaleDevices.serializer(), response.body)
            .fold(
              ifLeft = { RequestResult.ApplicationError(it.cause) },
              ifRight = { RequestResult.NonSuccess(SendMessageError.StaleDevicesError(it)) }
            )
        }
        428 -> {
          SignalJson
            .decode(ProofRequiredResponseBody.serializer(), response.body)
            .fold(
              ifLeft = { RequestResult.ApplicationError(it.cause) },
              ifRight = { RequestResult.NonSuccess(SendMessageError.ChallengeRequired(it.token, it.options, response.retryAfter())) }
            )
        }
        429 -> RequestResult.NonSuccess(SendMessageError.RateLimited(response.retryAfter()))
        508 -> RequestResult.NonSuccess(SendMessageError.ServerRejected)
        else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.status}"))
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  @Serializable
  data class SendMessageRequest(
    val messages: List<Message>,
    val timestamp: Long,
    val online: Boolean = false,
    val urgent: Boolean = true
  )

  @Serializable
  data class Message(
    val type: Int,
    val destinationDeviceId: Int,
    val destinationRegistrationId: Int,
    val content: String
  )

  @Serializable
  data class SendMessageResponse(
    val needsSync: Boolean = false,
    @Transient val sentUnidentified: Boolean = false
  )

  @Serializable
  data class MismatchedDevices(
    val missingDevices: List<Int> = emptyList(),
    val extraDevices: List<Int> = emptyList()
  )

  @Serializable
  data class StaleDevices(
    val staleDevices: List<Int> = emptyList()
  )

  /**
   * Body of a 428 response. [token] is the proof-required challenge token; [options] is the
   * list of supported challenge mechanisms (e.g. "captcha", "pushChallenge").
   */
  @Serializable
  private data class ProofRequiredResponseBody(
    val token: String,
    val options: List<String> = emptyList()
  )

  sealed class SendMessageError : BadRequestError {
    data object Unauthorized : SendMessageError()
    data object NotRegistered : SendMessageError()
    data class MismatchedDevicesError(val devices: MismatchedDevices) : SendMessageError()
    data class StaleDevicesError(val devices: StaleDevices) : SendMessageError()
    data class ChallengeRequired(val token: String, val options: List<String>, val retryAfter: Duration?) : SendMessageError()
    data class RateLimited(val retryAfter: Duration?) : SendMessageError()
    data object ServerRejected : SendMessageError()
  }
}
