/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.io.IOException
import kotlin.time.Duration

/**
 * Sends an [EnvelopeContent] to a single recipient, driving the full one-to-one flow:
 * encrypt-per-device, send, recover mismatched / stale devices by fetching prekeys and rebuilding sessions.
 *
 * All server interaction is delegated to [MessageApiV2] and [KeysApiV2]. Encryption is delegated to
 * [cipher]. Session state is read from (and archived via) [protocolStore] under [sessionLock].
 *
 * Internal helpers return [Either] of [SendError] so orchestration is driven entirely by return
 * values rather than exceptions. Libsignal's checked exceptions (from `cipher.encrypt` and session
 * building) are caught at the single point they can be raised and `raise`d into the matching
 * [SendError] variant.
 *
 * Sync transcripts are the caller's responsibility — issue a second [sendMessage] to the local address
 * with a SyncMessage.Sent payload after a successful primary send.
 */
open class MessageService(
  private val localAddress: SignalServiceAddress,
  private val localDeviceId: Int,
  private val messageApi: MessageApiV2,
  private val keysApi: KeysApiV2,
  private val protocolStore: SignalServiceAccountDataStore,
  private val sessionLock: SignalSessionLock,
  private val cipher: SignalServiceCipher,
  private val maxContentSizeBytes: Long = 0L
) {

  companion object {
    private val TAG = Log.tag(MessageService::class)

    private const val MAX_DEVICE_RECOVERY_ATTEMPTS = 3
  }

  private val localProtocolAddress: SignalProtocolAddress = SignalProtocolAddress(localAddress.identifier, localDeviceId)

  /**
   * Sends [envelopeContent] to [recipient]. Handles things like establishing sessions with newly-discovered linked devices.
   */
  suspend fun sendMessage(
    recipient: SignalServiceAddress,
    envelopeContent: EnvelopeContent,
    timestamp: Long,
    sealedSenderAccess: SealedSenderAccess?,
    story: Boolean,
    isOnline: Boolean,
    urgent: Boolean = true,
    onEncrypted: (() -> Unit)? = null
  ): Either<SendError, SendSuccess> = withContext(Dispatchers.IO) {
    either {
      val contentSize = envelopeContent.size().toLong()
      if (maxContentSizeBytes > 0 && contentSize > maxContentSizeBytes) {
        Log.w(TAG, "Content size $contentSize exceeds limit of $maxContentSizeBytes bytes; aborting send.")
        raise(SendError.ContentTooLarge(size = contentSize, maxAllowed = maxContentSizeBytes))
      }

      var encryptedReported = false

      // Certain errors self-resolve by mutating external state, like creating new sessions.
      // Trying several times in a loop lets us re-read that external state and use it in the next attempt.
      for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
        val encrypted = encryptForAllDevices(recipient, envelopeContent, sealedSenderAccess)

        if (!encryptedReported) {
          onEncrypted?.invoke()
          encryptedReported = true
        }

        val request = MessageApiV2.SendMessageRequest(
          messages = encrypted.map { it.toWireMessage() },
          timestamp = timestamp,
          online = isOnline,
          urgent = urgent
        )

        when (val result = messageApi.sendMessage(recipient.identifier, request, sealedSenderAccess, story)) {
          is RequestResult.Success -> {
            val response = result.result
            val devices = encrypted.map { it.destinationDeviceId }
            return@either SendSuccess(envelopeContent = envelopeContent, sentUnidentified = response.sentUnidentified, devices = devices)
          }
          is RequestResult.NonSuccess -> when (val err = result.error) {
            is MessageApiV2.SendMessageError.MismatchedDevicesError -> {
              handleMismatched(recipient, err.devices, sealedSenderAccess)
            }
            is MessageApiV2.SendMessageError.StaleDevicesError -> {
              for (deviceId in err.devices.staleDevices) {
                protocolStore.archiveSession(SignalProtocolAddress(recipient.identifier, deviceId))
              }
            }
            MessageApiV2.SendMessageError.Unauthorized -> raise(SendError.Unauthorized)
            MessageApiV2.SendMessageError.NotRegistered -> raise(SendError.NotRegistered)
            is MessageApiV2.SendMessageError.ChallengeRequired -> raise(SendError.ChallengeRequired(err.token, err.options, err.retryAfter))
            MessageApiV2.SendMessageError.ServerRejected -> raise(SendError.ServerRejected)
            is MessageApiV2.SendMessageError.RateLimited -> raise(SendError.RateLimited(err.retryAfter))
          }
          is RequestResult.RetryableNetworkError -> raise(SendError.NetworkError(result.networkError))
          is RequestResult.ApplicationError -> raise(SendError.ApplicationError(result.cause))
        }
      }

      Log.w(TAG, "Exhausted device-recovery attempts for ${recipient.identifier}")
      raise(SendError.SessionAttemptsExhausted)
    }
  }

  private fun Raise<SendError>.encryptForAllDevices(
    recipient: SignalServiceAddress,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?
  ): List<OutgoingPushMessage> {
    return targetDeviceIds(recipient).map { deviceId ->
      val address = SignalProtocolAddress(recipient.identifier, deviceId)
      encryptContent(recipient, address, envelopeContent, sealedSenderAccess)
    }
  }

  private fun Raise<SendError>.encryptContent(
    recipient: SignalServiceAddress,
    address: SignalProtocolAddress,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?
  ): OutgoingPushMessage = try {
    cipher.encrypt(address, sealedSenderAccess, envelopeContent)
  } catch (e: UntrustedIdentityException) {
    raise(SendError.IdentityMismatch(recipient, e))
  } catch (e: InvalidKeyException) {
    raise(SendError.ApplicationError(e))
  }

  private fun targetDeviceIds(recipient: SignalServiceAddress): List<Int> {
    val subDevices: MutableSet<Int> = (protocolStore.getSubDeviceSessions(recipient.identifier) + SignalServiceAddress.DEFAULT_DEVICE_ID).toMutableSet()

    // When sending to self, skip our own device.
    if (recipient.matches(localAddress)) {
      subDevices -= localDeviceId
    }

    return subDevices
      .filter { it == SignalServiceAddress.DEFAULT_DEVICE_ID || protocolStore.containsSession(SignalProtocolAddress(recipient.identifier, it)) }
      .toList()
  }

  /**
   * Initialize a session with the target address, which requires fetching a prekey bundle.
   */
  @VisibleForTesting
  internal open suspend fun Raise<SendError>.initializeSession(
    recipient: SignalServiceAddress,
    address: SignalProtocolAddress,
    sealedSenderAccess: SealedSenderAccess?
  ) {
    val response = when (val result = keysApi.getPreKey(address.serviceId.toServiceIdString(), address.deviceId, sealedSenderAccess)) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> {
        when (val e = result.error) {
          KeysApiV2.GetPreKeysError.Unauthorized -> raise(SendError.Unauthorized)
          KeysApiV2.GetPreKeysError.NotFound -> raise(SendError.PreKeyUnavailable("No prekeys found for $address"))
          is KeysApiV2.GetPreKeysError.RateLimited -> raise(SendError.RateLimited(e.retryAfter))
        }
      }
      is RequestResult.RetryableNetworkError -> raise(SendError.NetworkError(result.networkError))
      is RequestResult.ApplicationError -> raise(SendError.ApplicationError(result.cause))
    }

    val item = response.devices.firstOrNull { it.deviceId == address.deviceId }
      ?: raise(SendError.PreKeyUnavailable("No prekey for $address"))

    val bundle = buildPreKeyBundle(response.identityKey, item, address)

    try {
      SignalSessionBuilder(sessionLock, SessionBuilder(protocolStore, address, localProtocolAddress)).process(bundle)
    } catch (e: UntrustedIdentityException) {
      raise(SendError.IdentityMismatch(recipient, e))
    } catch (e: InvalidKeyException) {
      raise(SendError.ApplicationError(e))
    }
  }

  private suspend fun Raise<SendError>.handleMismatched(
    recipient: SignalServiceAddress,
    mismatched: MessageApiV2.MismatchedDevices,
    sealedSenderAccess: SealedSenderAccess?
  ) {
    for (extra in mismatched.extraDevices) {
      protocolStore.archiveSession(SignalProtocolAddress(recipient.identifier, extra))
    }

    for (missing in mismatched.missingDevices) {
      val address = SignalProtocolAddress(recipient.identifier, missing)
      initializeSession(recipient, address, sealedSenderAccess)
    }
  }

  private fun OutgoingPushMessage.toWireMessage(): MessageApiV2.Message = MessageApiV2.Message(
    type = type,
    destinationDeviceId = destinationDeviceId,
    destinationRegistrationId = destinationRegistrationId,
    content = content
  )

  private fun Raise<SendError>.buildPreKeyBundle(
    identityKey: ByteArray,
    item: KeysApiV2.PreKeyResponseItem,
    address: SignalProtocolAddress
  ): PreKeyBundle {
    val signedPreKey = item.signedPreKey ?: raise(SendError.PreKeyUnavailable("No signed prekey for $address"))
    val kyberPreKey = item.pqPreKey ?: raise(SendError.PreKeyUnavailable("No kyber prekey for $address"))

    return try {
      PreKeyBundle(
        item.registrationId,
        item.deviceId,
        item.preKey?.keyId?.toInt() ?: PreKeyBundle.NULL_PRE_KEY_ID,
        item.preKey?.let { ECPublicKey(it.publicKey) },
        signedPreKey.keyId.toInt(),
        ECPublicKey(signedPreKey.publicKey),
        signedPreKey.signature,
        IdentityKey(identityKey),
        kyberPreKey.keyId.toInt(),
        KEMPublicKey(kyberPreKey.publicKey, 0, kyberPreKey.publicKey.size),
        kyberPreKey.signature
      )
    } catch (e: InvalidKeyException) {
      raise(SendError.ApplicationError(e))
    }
  }

  /**
   * Send completed successfully.
   *
   * [devices] is the set of recipient devices the encrypted payload was delivered to. Callers persisting
   * a [org.thoughtcrime.securesms.database.MessageSendLogTables] entry (or a pending PNI signature record)
   * need this to know which sessions the recipient may later reference in a retry receipt.
   */
  data class SendSuccess(
    val envelopeContent: EnvelopeContent,
    val sentUnidentified: Boolean,
    val devices: List<Int>
  )

  sealed interface SendError {
    /** You discovered a safety number change during sending. */
    data class IdentityMismatch(val recipient: SignalServiceAddress, val cause: UntrustedIdentityException) : SendError

    /** The recipient is no longer registered. */
    data object NotRegistered : SendError

    /** Invalid credentials. You are likely no longer registered. */
    data object Unauthorized : SendError

    /**
     * The server wants you to complete a push challenge/captcha before continuing.
     * [token] is the challenge token; [options] enumerates the supported challenge mechanisms
     * (e.g. "captcha", "pushChallenge"). [retryAfter] is the Retry-After hint, if provided.
     */
    data class ChallengeRequired(val token: String, val options: List<String>, val retryAfter: Duration?) : SendError

    /** The server has fully rejected your request. This usually only happens during times of turmoil. Fail and require user action to resend. */
    data object ServerRejected : SendError

    /**
     * The encoded content exceeded the configured size cap. Permanent failure for this message —
     * retrying with the same content won't help.
     */
    data class ContentTooLarge(val size: Long, val maxAllowed: Long) : SendError

    /**
     * Each send attempt may result in us having to establish sessions with linked devices and such. This indicates that we hit our max attempt count while
     * trying to handle these situations. It should be safe to retry with normal backoff.
     */
    data object SessionAttemptsExhausted : SendError

    /** We needed to establish a session, but the server was missing either a signed or kyber prekey for the user. */
    data class PreKeyUnavailable(val reason: String) : SendError

    /** You're rate-limited. Use the [retryAfter] for your backoff. */
    data class RateLimited(val retryAfter: Duration?) : SendError

    /** A generic, retryable network error. */
    data class NetworkError(val cause: IOException) : SendError

    /** An unexpected error. You should likely crash. */
    data class ApplicationError(val cause: Throwable) : SendError
  }
}
