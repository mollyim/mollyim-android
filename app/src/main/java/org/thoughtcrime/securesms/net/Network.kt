package org.thoughtcrime.securesms.net

import okhttp3.Dns
import java.io.IOException
import java.net.Proxy
import javax.net.SocketFactory

object Network {
  @JvmStatic
  var isEnabled: Boolean = false

  private fun throwIfDisabled() {
    if (!isEnabled) throw IOException("Network is disabled")
  }

  @JvmStatic
  val socketFactory: SocketFactory = ProxySocketFactory(
    ProxyProvider {
      throwIfDisabled()
      proxy ?: throw IOException("Proxy address not available yet")
    }
  )

  @JvmStatic
  var socksProxy: SocksProxy? = null

  @JvmStatic
  val proxy
    get(): Proxy? {
      return socksProxy?.makeProxy() ?: Proxy.NO_PROXY
    }

  @JvmStatic
  val dns = Dns { hostname ->
    throwIfDisabled()
    if (socksProxy == null) {
      sequentialDns.lookup(hostname)
    } else {
      dnsOverHttps.lookup(hostname)
    }
  }

  private val dnsOverHttps: Dns = DohClient("https://1.1.1.1/dns-query", socketFactory)

  private val sequentialDns: Dns = SequentialDns(
    Dns.SYSTEM,
    dnsOverHttps,
  )
}
