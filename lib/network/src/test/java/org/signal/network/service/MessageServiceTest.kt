/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class MessageServiceTest {

  private val messageApi: MessageApiV2 = mockk()
  private val keysApi: KeysApiV2 = mockk()
  private val protocolStore: SignalServiceAccountDataStore = mockk(relaxUnitFun = true)
  private val sessionLock: SignalSessionLock = mockk()
  private val cipher: SignalServiceCipher = mockk()

  private val localAci = ServiceId.ACI.from(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
  private val localAddress = SignalServiceAddress(localAci)

  private val recipientAci = ServiceId.ACI.from(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"))
  private val recipient = SignalServiceAddress(recipientAci)

  private val timestamp = 1_700_000_000L
  private val envelopeContent: EnvelopeContent = mockk {
    every { size() } returns 0
  }

  @Test
  fun `happy path with existing session returns Success`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(SignalProtocolAddress(recipient.identifier, 1)) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returns
      RequestResult.Success(MessageApiV2.SendMessageResponse(sentUnidentified = true))

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.sentUnidentified).isEqualTo(true)
  }

  @Test
  fun `isOnline true is forwarded to the send request`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(SignalProtocolAddress(recipient.identifier, 1)) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")
    coEvery { messageApi.sendMessage(any(), any(), any(), any()) } returns
      RequestResult.Success(MessageApiV2.SendMessageResponse())

    service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = true)

    coVerify {
      messageApi.sendMessage(
        recipient.identifier,
        match<MessageApiV2.SendMessageRequest> { it.online },
        null,
        false
      )
    }
  }

  @Test
  fun `sub-device without session is excluded from target devices`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns listOf(2, 3)
    every { protocolStore.containsSession(SignalProtocolAddress(recipient.identifier, 2)) } returns true
    every { protocolStore.containsSession(SignalProtocolAddress(recipient.identifier, 3)) } returns false
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returns
      RequestResult.Success(MessageApiV2.SendMessageResponse())

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isInstanceOf(Either.Right::class)
    verify { cipher.encrypt(SignalProtocolAddress(recipient.identifier, 1), any(), any()) }
    verify { cipher.encrypt(SignalProtocolAddress(recipient.identifier, 2), any(), any()) }
    verify(exactly = 0) { cipher.encrypt(SignalProtocolAddress(recipient.identifier, 3), any(), any()) }
  }

  @Test
  fun `409 MismatchedDevices archives extras, fetches missing prekeys, and retries`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    val mismatched = MessageApiV2.MismatchedDevices(missingDevices = listOf(2), extraDevices = listOf(5))
    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returnsMany listOf(
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.MismatchedDevicesError(mismatched)),
      RequestResult.Success(MessageApiV2.SendMessageResponse())
    )
    coEvery { keysApi.getPreKey(recipient.identifier, 2, null) } returns
      RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0), devices = emptyList()))

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isInstanceOf(Either.Right::class)
    verify { protocolStore.archiveSession(SignalProtocolAddress(recipient.identifier, 5)) }
    coVerify { keysApi.getPreKey(recipient.identifier, 2, null) }
  }

  @Test
  fun `410 StaleDevices archives stales and retries`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    val stale = MessageApiV2.StaleDevices(staleDevices = listOf(3))
    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returnsMany listOf(
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.StaleDevicesError(stale)),
      RequestResult.Success(MessageApiV2.SendMessageResponse())
    )

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isInstanceOf(Either.Right::class)
    verify { protocolStore.archiveSession(SignalProtocolAddress(recipient.identifier, 3)) }
  }

  @Test
  fun `repeated device conflicts exhaust retries`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    val stale = MessageApiV2.StaleDevices(staleDevices = listOf(4))
    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.StaleDevicesError(stale))

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.SessionAttemptsExhausted))
  }

  @Test
  fun `401 maps to Unauthorized`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")
    coEvery { messageApi.sendMessage(any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.Unauthorized)

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.Unauthorized))
  }

  @Test
  fun `404 maps to NotRegistered`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")
    coEvery { messageApi.sendMessage(any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.NotRegistered)

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.NotRegistered))
  }

  @Test
  fun `send 429 propagates retry-after duration via SendResult RateLimited`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")
    coEvery { messageApi.sendMessage(any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.RateLimited(retryAfter = 30.seconds))

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.RateLimited(retryAfter = 30.seconds)))
  }

  @Test
  fun `prekey 429 during mismatched-device recovery propagates retry-after as RateLimited`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    val mismatched = MessageApiV2.MismatchedDevices(missingDevices = listOf(2), extraDevices = emptyList())
    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.MismatchedDevicesError(mismatched))
    coEvery { keysApi.getPreKey(recipient.identifier, 2, null) } returns
      RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.RateLimited(retryAfter = 60.seconds))

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.RateLimited(retryAfter = 60.seconds)))
  }

  @Test
  fun `IOException from send maps to NetworkError`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")
    val ioError = IOException("down")
    coEvery { messageApi.sendMessage(any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(ioError)

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    val network = (result as Either.Left).value as MessageService.SendError.NetworkError
    assertThat(network.cause).isEqualTo(ioError)
  }

  @Test
  fun `UntrustedIdentityException during encryption maps to IdentityMismatch`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    val untrusted = UntrustedIdentityException(recipient.identifier)
    every { cipher.encrypt(any(), any(), any()) } throws untrusted

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    val mismatch = (result as Either.Left).value as MessageService.SendError.IdentityMismatch
    assertThat(mismatch.cause).isEqualTo(untrusted)
  }

  @Test
  fun `prekey fetch 404 during mismatched-device recovery propagates as PreKeyUnavailable`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipient.identifier) } returns emptyList()
    every { protocolStore.containsSession(any()) } returns true
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "payload")

    val mismatched = MessageApiV2.MismatchedDevices(missingDevices = listOf(2), extraDevices = emptyList())
    coEvery { messageApi.sendMessage(recipient.identifier, any(), null, false) } returns
      RequestResult.NonSuccess(MessageApiV2.SendMessageError.MismatchedDevicesError(mismatched))
    coEvery { keysApi.getPreKey(recipient.identifier, 2, null) } returns
      RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.NotFound)

    val result = service.sendMessage(recipient, envelopeContent, timestamp, sealedSenderAccess = null, story = false, isOnline = false)

    val left = (result as Either.Left).value
    assertThat(left).isInstanceOf(MessageService.SendError.PreKeyUnavailable::class)
  }

  /**
   * Spy with `initializeSession` stubbed so tests don't exercise real crypto / native session building.
   * The stub still invokes [KeysApiV2.getPreKey] and forwards non-success [RequestResult]s as the real
   * implementation would; happy path is a no-op.
   */
  private fun newService(): MessageService {
    val spy: MessageService = spyk(
      MessageService(
        localAddress = localAddress,
        localDeviceId = 1,
        messageApi = messageApi,
        keysApi = keysApi,
        protocolStore = protocolStore,
        sessionLock = sessionLock,
        cipher = cipher
      )
    )
    coEvery {
      with(spy) {
        any<Raise<MessageService.SendError>>().initializeSession(any(), any(), any())
      }
    } coAnswers {
      val raiseArg = arg<Raise<MessageService.SendError>>(0)
      val addressArg = arg<SignalProtocolAddress>(2)
      val sealedArg = arg<SealedSenderAccess?>(3)
      when (val r = keysApi.getPreKey(addressArg.name, addressArg.deviceId, sealedArg)) {
        is RequestResult.Success -> Unit
        is RequestResult.NonSuccess -> raiseArg.raise(
          when (val e = r.error) {
            KeysApiV2.GetPreKeysError.Unauthorized -> MessageService.SendError.Unauthorized
            KeysApiV2.GetPreKeysError.NotFound -> MessageService.SendError.PreKeyUnavailable("No prekeys found for $addressArg")
            is KeysApiV2.GetPreKeysError.RateLimited -> MessageService.SendError.RateLimited(e.retryAfter)
          }
        )
        is RequestResult.RetryableNetworkError -> raiseArg.raise(MessageService.SendError.NetworkError(r.networkError))
        is RequestResult.ApplicationError -> raiseArg.raise(MessageService.SendError.ApplicationError(r.cause))
      }
    }
    return spy
  }
}
