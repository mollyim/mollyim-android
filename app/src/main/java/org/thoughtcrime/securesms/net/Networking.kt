package org.thoughtcrime.securesms.net

import okhttp3.Dns
import org.signal.libsignal.net.Network
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.net.SocketFactory

object Networking {

  @JvmStatic
  var isEnabled: Boolean = false

  @JvmStatic
  val socketFactory: SocketFactory = ProxySocketFactory {
    ensureNetworkEnabled()
    proxy.also { validateProxyAddress(it) }
  }

  @JvmStatic
  var socksProxy: SocksProxy? = null

  /**
   * A SOCKS [Proxy] instance pointing to the "discard" service on localhost (port 9),
   * effectively a black hole for outgoing network traffic.
   */
  @JvmStatic
  val DUMMY_PROXY = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("localhost", 9))

  /**
   * Retrieves the current proxy configuration.
   *
   * It checks if a proxy is configured and attempts to create a [Proxy] instance
   * using the specified socket address. If the address is invalid or unavailable,
   * it defaults to [DUMMY_PROXY] to prevent IP leaks.
   *
   * If no proxy is configured, it returns [Proxy.NO_PROXY] to indicate direct
   * connection without a proxy.
   */
  @JvmStatic
  val proxy: Proxy
    get() {
      val currentSocksProxy = socksProxy ?: return Proxy.NO_PROXY
      return currentSocksProxy.makeProxy() ?: DUMMY_PROXY
    }

  @JvmStatic
  val proxySelectorForSocks = object : ProxySelector() {
    private val systemDefault = getDefault()

    override fun select(uri: URI?): List<Proxy> {
      return if (socksProxy != null) {
        // Do not chain to system proxy if SOCKS proxy is selected
        listOf(Proxy.NO_PROXY)
      } else {
        systemDefault.select(uri)
      }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) =
      systemDefault.connectFailed(uri, sa, ioe)
  }

  fun configureLibsignalProxy(network: Network, signalUrl: String) {
    // If networking is disabled globally, block new connections
    if (!isEnabled) {
      network.setInvalidProxy()
      return
    }

    // Use explicit SOCKS proxy override if available
    val currentProxy = proxy
    if (currentProxy.type() == Proxy.Type.SOCKS) {
      val (host, port) = currentProxy.toHostPort()!!
      network.setProxy("socks5h", host, port, null, null)
      return
    }

    // Resolve system proxy for the Signal URL
    val systemProxy: Proxy? = runCatching {
      ProxySelector.getDefault().select(URI.create(signalUrl)).firstOrNull()
    }.getOrNull()

    val (scheme, hostPort) = when (systemProxy?.type()) {
      Proxy.Type.HTTP -> "http" to systemProxy.toHostPort()
      Proxy.Type.SOCKS -> "socks5" to systemProxy.toHostPort()
      else -> null to null
    }

    if (hostPort != null) {
      val (host, port) = hostPort
      network.setProxy(scheme, host, port, null, null)
    } else {
      network.clearProxy()
    }
  }

  private fun Proxy.toHostPort(): Pair<String, Int>? =
    (address() as? InetSocketAddress)?.let { it.hostString to it.port }

  @JvmStatic
  val dns: Dns = DnsProxyAware()

  private fun ensureNetworkEnabled() {
    if (!isEnabled) throw IOException("Network is disabled")
  }

  private fun validateProxyAddress(proxy: Proxy) {
    if (proxy == DUMMY_PROXY) throw IOException("Proxy socket address not available yet")
  }

  private class DnsProxyAware : Dns {
    private val cloudflare: Dns = DohClient("https://1.1.1.1/dns-query", socketFactory, proxySelectorForSocks)
    private val quad9: Dns = DohClient("https://9.9.9.9/dns-query", socketFactory, proxySelectorForSocks)

    private val systemDnsResolver: Dns = SequentialDns(Dns.SYSTEM, cloudflare, quad9)
    private val dohDnsResolver: Dns = SequentialDns(cloudflare, quad9)

    override fun lookup(hostname: String): List<InetAddress> {
      ensureNetworkEnabled()
      return if (socksProxy == null) {
        systemDnsResolver.lookup(hostname)
      } else {
        dohDnsResolver.lookup(hostname)
      }
    }
  }
}
