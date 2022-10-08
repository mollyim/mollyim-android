package org.thoughtcrime.securesms.net

import okhttp3.Dns
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.net.SocketFactory

object Network {
  @JvmStatic
  var isEnabled: Boolean = false

  private fun throwIfDisabled() {
    if (!isEnabled) throw IOException("Network is disabled")
  }

  @JvmStatic
  val socketFactory: SocketFactory = ProxySocketFactory {
    throwIfDisabled()
    proxy ?: throw IOException("Proxy address not available yet")
  }

  @JvmStatic
  var socksProxy: SocksProxy? = null

  @JvmStatic
  val proxy
    get(): Proxy? {
      return socksProxy?.makeProxy() ?: Proxy.NO_PROXY
    }

  @JvmStatic
  val proxySelectorForSocks = object : ProxySelector() {
    val systemDefault = getDefault()

    override fun select(uri: URI?): List<Proxy> {
      return if (socksProxy != null) {
        // Do not chain to system proxy if SOCKS proxy is selected
        listOf(Proxy.NO_PROXY)
      } else {
        systemDefault.select(uri)
      }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = systemDefault.connectFailed(uri, sa, ioe)
  }

  @JvmStatic
  val dns = Dns { hostname ->
    throwIfDisabled()
    if (socksProxy == null) {
      systemResolver.lookup(hostname)
    } else {
      dohResolver.lookup(hostname)
    }
  }

  private val cloudflare: Dns = DohClient("https://1.1.1.1/dns-query", socketFactory, proxySelectorForSocks)

  private val quad9: Dns = DohClient("https://9.9.9.9/dns-query", socketFactory, proxySelectorForSocks)

  private val systemResolver: Dns = SequentialDns(
    Dns.SYSTEM,
    cloudflare,
    quad9,
  )

  private val dohResolver: Dns = SequentialDns(
    cloudflare,
    quad9,
  )
}
