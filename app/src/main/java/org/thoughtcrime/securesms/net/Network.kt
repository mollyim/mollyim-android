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
      if (socksProxy != null) {
        return socksProxy?.makeProxy()
      } else {
        return Proxy.NO_PROXY
      }
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

  private val cloudflare: Dns = DohClient("https://1.1.1.1/dns-query", socketFactory)

  private val quad9: Dns = DohClient("https://9.9.9.9/dns-query", socketFactory)

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
