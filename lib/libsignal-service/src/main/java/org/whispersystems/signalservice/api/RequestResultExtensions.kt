/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.signal.core.util.concurrent.safeBlockingGet
import org.signal.libsignal.net.RequestResult
import org.signal.network.exceptions.PushNetworkException
import org.signal.network.rest.RestStatusCodeError
import org.signal.network.util.JsonUtil
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.time.Duration

/**
 * A convenience method to convert a websocket request into a [RequestResult], mirroring
 * [NetworkResult.fromWebSocketRequest] for callers that want the libsignal [RequestResult] surface while still
 * sending over the [SignalWebSocket]. Non-2xx responses become [RequestResult.NonSuccess] carrying a
 * [RestStatusCodeError]; transport failures become [RequestResult.RetryableNetworkError].
 */
fun <T : Any> SignalWebSocket.fromWebSocketRequest(
  request: WebSocketRequestMessage,
  clazz: KClass<T>,
  timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT
): RequestResult<T, RestStatusCodeError> {
  return try {
    val result: Result<RequestResult<T, RestStatusCodeError>> = request(request, timeout)
      .map { response: WebsocketResponse -> Result.success(response.toRequestResult(clazz)) }
      .onErrorReturn { Result.failure(it) }
      .safeBlockingGet()

    result.getOrThrow()
  } catch (e: IOException) {
    RequestResult.RetryableNetworkError(e)
  } catch (e: TimeoutException) {
    RequestResult.RetryableNetworkError(PushNetworkException(e))
  } catch (e: InterruptedException) {
    RequestResult.RetryableNetworkError(PushNetworkException(e))
  } catch (e: Throwable) {
    RequestResult.ApplicationError(e)
  }
}

private fun <T : Any> WebsocketResponse.toRequestResult(clazz: KClass<T>): RequestResult<T, RestStatusCodeError> {
  return if (status < 200 || status > 299) {
    RequestResult.NonSuccess(RestStatusCodeError(status, headers, body?.toByteArray()))
  } else {
    val value: T = when (clazz) {
      Unit::class -> clazz.cast(Unit)
      String::class -> clazz.cast(body)
      else -> JsonUtil.fromJson(body, clazz.java)
    }
    RequestResult.Success(value)
  }
}
