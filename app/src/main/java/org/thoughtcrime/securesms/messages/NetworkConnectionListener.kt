/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Observes changes in internet connectivity and notifies when the connection is lost or regained.
 *
 * The [onNetworkLost] callback is triggered with a Boolean indicating whether the Internet connection
 * is lost or regained. The current connection state is also provided immediately upon registration.
 */
class NetworkConnectionListener(context: Context, private val onNetworkLost: (() -> Boolean) -> Unit) {
  companion object {
    private val TAG = Log.tag(NetworkConnectionListener::class.java)
  }

  private val connectivityManager = ServiceUtil.getConnectivityManager(context)

  inner class NetworkStateCallback : ConnectivityManager.NetworkCallback() {

    private val currentNetworks = mutableMapOf<Network, Boolean>()

    private var lastConnectionState: Boolean? = null

    private fun updateConnectionState(hasConnection: Boolean) {
      lastConnectionState = hasConnection
      onNetworkLost { !hasConnection }
    }

    private fun connectionChanged() {
      synchronized(this) {
        val hasConnection = currentNetworks.any { it.value }
        if (lastConnectionState != hasConnection) {
          updateConnectionState(hasConnection)
        }
      }
    }

    fun setInitialConnectionState(network: Network?, isAvailable: Boolean) {
      Log.d(TAG, "Initial state: Network $network ${if (isAvailable) "UP" else "DOWN"}")

      synchronized(this) {
        if (lastConnectionState == null) {
          if (network != null && isAvailable) {
            currentNetworks[network] = true
            updateConnectionState(hasConnection = true)
          } else {
            updateConnectionState(hasConnection = false)
          }
        } else {
          Log.d(TAG, "Initial state already set, skipping")
        }
      }
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
      Log.d(TAG, "Network $network UP and ${if (blocked) "BLOCKED" else "UNBLOCKED"}")
      currentNetworks[network] = !blocked
      connectionChanged()
    }

    override fun onLost(network: Network) {
      Log.d(TAG, "Network $network LOST")
      currentNetworks.remove(network)
      connectionChanged()
    }
  }

  private var networkStateCallback: NetworkStateCallback? = null

  @Synchronized
  fun register() {
    if (networkStateCallback != null) return

    val request =
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    val callback = NetworkStateCallback()
    networkStateCallback = callback

    connectivityManager.registerNetworkCallback(request, callback)

    val network = connectivityManager.activeNetwork
    val hasInternet = connectivityManager.getNetworkCapabilities(network)
      ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      ?: false

    callback.setInitialConnectionState(network, isAvailable = hasInternet)
  }

  @Synchronized
  fun unregister() {
    networkStateCallback?.let { callback ->
      connectivityManager.unregisterNetworkCallback(callback)
      networkStateCallback = null
    }
  }
}
