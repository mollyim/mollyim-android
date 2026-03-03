/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.provisioning

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import java.io.Closeable

/**
 * Shared provisioning socket lifecycle for QR-based provisioning flows.
 */
class ProvisioningSocketController<T>(
  private val configuration: SignalServiceConfiguration,
  private val scope: CoroutineScope,
  private val mode: ProvisioningSocket.Mode,
  private val onEvent: (Event<T>) -> Unit,
) {

  companion object {
    private val TAG = Log.tag(ProvisioningSocketController::class)
  }

  sealed interface Event<out T> {
    data class QrReady(val socketId: Int, val qrData: QrCodeData) : Event<Nothing>
    data class ProvisionMessageReady<T>(val message: T) : Event<T>
    data class InvalidProvisioningPayload(val socketId: Int) : Event<Nothing>
    data class SocketFailed(val socketId: Int, val throwable: Throwable) : Event<Nothing>
  }

  private var socketHandles: MutableList<Closeable> = mutableListOf()
  private var startNewSocketJob: Job? = null
  @Volatile private var currentSocketId: Int? = null

  fun restart() {
    shutdown()
    startNewSocket()

    startNewSocketJob = scope.launch(Dispatchers.IO) {
      var count = 0
      while (count < 5 && isActive) {
        delay(ProvisioningSocket.LIFESPAN / 2)
        if (isActive) {
          startNewSocket()
          count++
          Log.d(TAG, "Started next provisioning websocket count: $count")
        }
      }
    }
  }

  fun shutdown() {
    startNewSocketJob?.cancel()
    currentSocketId = null
    synchronized(socketHandles) {
      socketHandles.forEach { it.close() }
      socketHandles.clear()
    }
  }

  private fun startNewSocket() {
    synchronized(socketHandles) {
      socketHandles += startSocket()

      if (socketHandles.size > 2) {
        socketHandles.removeAt(0).close()
      }
    }
  }

  private fun startSocket(): Closeable {
    return ProvisioningSocket.start<T>(
      mode = mode,
      identityKeyPair = IdentityKeyPair.generate(),
      configuration = configuration,
      handler = { id, throwable ->
        val current = currentSocketId
        if (current == null || current == id) {
          onEvent(Event.SocketFailed(id, throwable))
        } else {
          Log.i(TAG, "Ignoring stale socket failure [$id], current socket is [$current]")
        }
      }
    ) { socket ->
      val qrData = QrCodeData.forData(
        data = socket.getProvisioningUrl(),
        supportIconOverlay = false
      )
      currentSocketId = socket.id
      onEvent(Event.QrReady(socket.id, qrData))

      val result = socket.getProvisioningMessageDecryptResult()
      if (result is SecondaryProvisioningCipher.ProvisioningDecryptResult.Success) {
        onEvent(Event.ProvisionMessageReady(result.message))
      } else {
        val current = currentSocketId
        if (current == socket.id) {
          onEvent(Event.InvalidProvisioningPayload(socket.id))
        } else {
          Log.i(TAG, "Ignoring stale invalid payload from socket [${socket.id}], current socket is [$current]")
        }
      }
    }
  }
}

