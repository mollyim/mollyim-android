/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.zipWithPrevious

enum class ConnectivityState {
  OFFLINE,
  ONLINE,
  ONLINE_VPN,
  BLOCKED,
  BLOCKED_VPN;

  /** Returns true if network traffic expected to reach the Internet. */
  val hasInternet: Boolean
    get() = this == ONLINE || this == ONLINE_VPN
}

/**
 * Observes changes in network connectivity and notifies via [onReachabilityChanged].
 *
 * The current connectivity state is provided immediately upon registration
 * if the device already has network access.
 */
class InternetConnectivityMonitor(
  private val connectivityManager: ConnectivityManager,
  private val onReachabilityChanged: (ConnectivityState) -> Unit,
) {
  companion object {
    private val TAG = Log.tag(InternetConnectivityMonitor::class.java)
  }

  private data class NetworkState(
    val validated: Boolean,
    val blocked: Boolean,
    val onVpn: Boolean
  ) {
    val isReachable: Boolean get() = validated && !blocked

    companion object {
      val DOWN = NetworkState(validated = false, blocked = false, onVpn = false)
    }
  }

  private class NetworkAggregationCallback(
    private val onNetworkStateChanged: (NetworkState) -> Unit,
    private val onVpnLoss: () -> Unit,
  ) : ConnectivityManager.NetworkCallback() {

    // Tracks active networks and their states
    private val networks = mutableMapOf<Network, NetworkState>()

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      Log.d(TAG, "onCapabilitiesChanged($network, validated=$validated, vpn=$vpn)")
      val existing = networks[network]
      if (existing == null) {
        networks[network] = NetworkState(validated = validated, blocked = false, onVpn = vpn)
        // API 26+ guarantees that onLinkPropertiesChanged is always called next for new networks,
        // followed by onBlockedStatusChanged (on API 29+).
      } else {
        networks[network] = existing.copy(validated = validated, onVpn = vpn)
        onNetworkStateChanged(networks.bestNetworkState())
      }
    }

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
      if (Build.VERSION.SDK_INT < 29) {
        onNetworkStateChanged(networks.bestNetworkState())
      }
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
      Log.d(TAG, "onBlockedStatusChanged($network, blocked=$blocked)")
      val existing = networks[network] ?: return
      networks[network] = existing.copy(blocked = blocked)
      onNetworkStateChanged(networks.bestNetworkState())
    }

    override fun onLost(network: Network) {
      Log.d(TAG, "onLost($network)")
      val removed = networks.remove(network)
      if (removed?.onVpn == true) {
        onVpnLoss()
      } else {
        onNetworkStateChanged(networks.bestNetworkState())
      }
    }

    private fun Map<Network, NetworkState>.bestNetworkState(): NetworkState {
      return if (isEmpty()) {
        NetworkState.DOWN
      } else {
        // A VPN network is only considered validated if there's also an underlying
        // non-VPN network.
        val hasUnderlyingNet = values.any { !it.onVpn }
        val eligibleStates = values.map { state ->
          state.copy(
            validated = state.validated && hasUnderlyingNet
          )
        }
        eligibleStates.maxBy { it.rank() }
      }
    }

    private fun NetworkState.rank(): Int =
      when {
        isReachable && onVpn -> 3
        isReachable -> 2
        blocked -> 1
        else -> 0
      }
  }

  private fun internetConnectionFlow(): Flow<ConnectivityState> = callbackFlow {
    val callback = NetworkAggregationCallback(
      onNetworkStateChanged = {
        val connectivityState = when {
          it.isReachable && it.onVpn -> ConnectivityState.ONLINE_VPN
          it.isReachable -> ConnectivityState.ONLINE
          it.blocked && it.onVpn -> ConnectivityState.BLOCKED_VPN
          it.blocked -> ConnectivityState.BLOCKED
          else -> ConnectivityState.OFFLINE
        }
        // Should not block as we conflate the flow.
        trySendBlocking(connectivityState)
      },
      onVpnLoss = {
        // VPN transport disconnected. For always-on VPNs with a kill switch,
        // the underlying network may still appear "UP" but traffic is blocked.
        // Restart the flow to re-evaluate connectivity.
        close(NetworkStateStaleException("VPN loss"))
      }
    )

    val request = NetworkRequest.Builder()
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()

    connectivityManager.registerNetworkCallback(request, callback)

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }.conflate()

  private val scope = CoroutineScope(SignalDispatchers.IO)
  private var listenerJob: Job? = null

  @Synchronized
  fun register() {
    if (listenerJob != null) return

    listenerJob = scope.launch {
      internetConnectionFlow()
        .retryWhen { cause, _ ->
          val retrying = cause is NetworkStateStaleException
          Log.i(TAG, "Re-registering callback ($retrying): ${cause.message}")
          retrying
        }
        .distinctUntilChanged()
        .zipWithPrevious { prevState, state ->
          Log.i(TAG, buildString {
            append("Internet reachability: ")
            prevState?.let { append("$it -> ") }
            append(state)
          })
          state
        }.collect {
          onReachabilityChanged(it)
        }
    }
  }

  @Synchronized
  fun unregister() {
    listenerJob?.cancel()
    listenerJob = null
  }

  /**
   * Thrown when the tracked network state is no longer reliable.
   */
  class NetworkStateStaleException(message: String) : Exception(message)
}
