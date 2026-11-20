/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

import org.signal.core.util.logging.Log
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

sealed interface ProxyConfig {
  data object Direct : ProxyConfig

  data class ProxyAddress(
    val scheme: ProxyScheme,
    val host: String,
    val port: Int,
    val source: ProxySource,
  ) : ProxyConfig

  /** Supported proxy schemes by libsignal-net. */
  enum class ProxyScheme(val value: String) {
    SOCKS("socks5"),

    /**
     * MOLLY: SOCKS with remote DNS resolution. A user-configured proxy is always resolved remotely so that
     * hostnames are never handed to the local resolver (and never leak outside the proxy).
     */
    SOCKS_REMOTE_DNS("socks5h"),
    HTTP("http")
  }

  enum class ProxySource {
    USER,
    SYSTEM
  }

  companion object {
    private val TAG = Log.tag(ProxyConfig::class)

    @JvmStatic
    @JvmOverloads
    fun resolve(
      userProxy: Proxy,
      configuration: SignalServiceConfiguration,
      targetUrl: String,
      proxySelector: ProxySelector? = ProxySelector.getDefault()
    ): ProxyConfig {
      // MOLLY: userProxy is resolved first and authoritatively.
      if (userProxy != Proxy.NO_PROXY) {
        return fromProxy(userProxy, ProxySource.USER)!!
      }

      return when {
        configuration.censored -> Direct
        else -> resolveSystemProxy(targetUrl, proxySelector)?.let { fromProxy(it, ProxySource.SYSTEM) } ?: Direct
      }
    }

    /**
     * Resolves the system-configured [Proxy] for the given URL using [ProxySelector].
     *
     * @return null if no proxy is configured.
     */
    private fun resolveSystemProxy(targetUrl: String, proxySelector: ProxySelector?): Proxy? {
      return try {
        proxySelector?.select(URI.create(targetUrl))?.firstOrNull()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve the system proxy, falling back to direct.", e)
        null
      }
    }

    private fun fromProxy(proxy: Proxy, source: ProxySource): ProxyConfig? {
      val scheme = when (proxy.type()) {
        Proxy.Type.HTTP -> ProxyScheme.HTTP
        Proxy.Type.SOCKS -> when (source) {
          ProxySource.USER -> ProxyScheme.SOCKS_REMOTE_DNS
          ProxySource.SYSTEM -> ProxyScheme.SOCKS
        }

        Proxy.Type.DIRECT -> return null
      }

      val socketAddress = proxy.address() as? InetSocketAddress ?: return null

      // MOLLY: Loopback addresses are allowed: a user's proxy often runs locally.

      return ProxyAddress(scheme, socketAddress.hostString, socketAddress.port, source)
    }
  }
}
