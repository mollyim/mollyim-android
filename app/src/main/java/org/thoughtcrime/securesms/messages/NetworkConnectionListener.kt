/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import org.signal.core.util.logging.Log

data class NetworkState(
  val available: Boolean,
  val validated: Boolean,
  val httpProxy: ProxyInfo?,
) {
  val isReady: Boolean
    get() = available && validated

  companion object {
    val DOWN = NetworkState(available = false, validated = false, httpProxy = null)
  }

  override fun toString(): String {
    return buildString {
      append(
        when {
          available && validated -> "UP"
          available && !validated -> "BLOCKED"
          else -> "DOWN"
        }
      )
      append('/')
      append(if (httpProxy != null) "PROXY" else "NO-PROXY")
    }
  }
}

/**
 * Observes changes in network connectivity and notifies via [onNetworkChange].
 *
 * The current connection state is also provided immediately upon registration.
 */
class NetworkConnectionListener(
  val connectivityManager: ConnectivityManager,
  private val onNetworkChange: (NetworkState) -> Unit,
) {
  companion object {
    private val TAG = Log.tag(NetworkConnectionListener::class.java)
  }

  inner class NetworkStateCallback : ConnectivityManager.NetworkCallback() {

    // Tracks active networks and their states
    private val networks = mutableMapOf<Network, NetworkState>()

    // Last dispatched connection state (null until first set)
    private var connectionState: NetworkState? = null

    fun startMonitoring() {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      connectivityManager.registerNetworkCallback(request, this)

      val network = connectivityManager.activeNetwork
      val caps = connectivityManager.getNetworkCapabilities(network)
      val props = connectivityManager.getLinkProperties(network)
      val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
      val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
      val httpProxy = props?.httpProxy

      val initialState = NetworkState(
        available = hasInternet,
        validated = validated,
        httpProxy = httpProxy,
      )

      onInitialConnectionState(network, initialState)
    }

    fun stopMonitoring() {
      connectivityManager.unregisterNetworkCallback(this)
    }

    private fun connectivityChanged(): NetworkState? {
      val newState = networks.bestNetworkState()
      val hasChanged = newState != connectionState
      return if (hasChanged) {
        connectionState = newState
        Log.v(TAG, "Network state changed -> $newState")
        newState
      } else null
    }

    private fun NetworkState.dispatch() {
      onNetworkChange(this)
    }

    private fun Map<Network, NetworkState>.bestNetworkState(): NetworkState =
      values.firstOrNull { it.validated }
        ?: values.firstOrNull { it.available }
        ?: NetworkState.DOWN

    private fun onInitialConnectionState(network: Network?, state: NetworkState) {
      val maybeNew = synchronized(this) {
        if (connectionState == null) {
          if (network != null) {
            networks[network] = state
          }
          connectivityChanged()
        } else {
          Log.v(TAG, "Initial state skipped; already set: $connectionState")
          null
        }
      }

      maybeNew?.dispatch()
    }

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
      Log.d(TAG, "NetworkCallback onLinkPropertiesChanged($network)")
      val httpProxy = linkProperties.httpProxy
      val maybeNew = synchronized(this) {
        val existing = networks.getOrDefault(
          network,
          NetworkState(available = true, validated = false, httpProxy = httpProxy)
        )
        val state = existing.copy(httpProxy = httpProxy)
        networks[network] = state
        connectivityChanged()
      }

      maybeNew?.dispatch()
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
      Log.d(TAG, "NetworkCallback onBlockedStatusChanged($network, $blocked)")
      val validated = !blocked
      val maybeNew = synchronized(this) {
        val existing = networks.getOrDefault(
          network,
          NetworkState(available = true, validated = validated, httpProxy = null)
        )
        val state = existing.copy(validated = validated)
        networks[network] = state
        connectivityChanged()
      }

      maybeNew?.dispatch()
    }

    override fun onLost(network: Network) {
      Log.d(TAG, "NetworkCallback onLost($network)")
      val maybeNew = synchronized(this) {
        networks.remove(network)
        connectivityChanged()
      }

      maybeNew?.dispatch()
    }
  }

  private var networkStateCallback: NetworkStateCallback? = null

  @Synchronized
  fun register() {
    if (networkStateCallback == null) {
      networkStateCallback = NetworkStateCallback().apply {
        startMonitoring()
      }
    } else {
      Log.w(TAG, "Already registered")
    }
  }

  @Synchronized
  fun unregister() {
    networkStateCallback?.stopMonitoring()
    networkStateCallback = null
  }
}
