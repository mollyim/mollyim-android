package org.thoughtcrime.securesms.net

import okhttp3.Dns
import org.thoughtcrime.securesms.BuildConfig
import java.io.IOException
import java.net.Proxy
import javax.net.SocketFactory

object Network {
  @JvmStatic
  var isEnabled: Boolean = false

  private fun throwIfDisabled() {
    if (!isEnabled) throw IOException("Network is disabled")
  }

  private val proxyProvider: ProxyProvider = ProxyProvider {
    throwIfDisabled()
    proxy ?: throw IOException("Proxy address not available yet")
  }

  @JvmStatic
  val socketFactory: SocketFactory = ProxySocketFactory(proxyProvider)

  @JvmStatic
  var socksProxy: SocksProxy? = null

  @JvmStatic
  val proxy: Proxy? = socksProxy?.makeProxy() ?: Proxy.NO_PROXY

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
    CustomDns("1.1.1.1"),
    StaticDns(
      mapOf(
        BuildConfig.SIGNAL_URL.stripProtocol() to BuildConfig.SIGNAL_SERVICE_IPS.toSet(),
        BuildConfig.STORAGE_URL.stripProtocol() to BuildConfig.SIGNAL_STORAGE_IPS.toSet(),
        BuildConfig.SIGNAL_CDN_URL.stripProtocol() to BuildConfig.SIGNAL_CDN_IPS.toSet(),
        BuildConfig.SIGNAL_CDN2_URL.stripProtocol() to BuildConfig.SIGNAL_CDN2_IPS.toSet(),
        BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL.stripProtocol() to BuildConfig.SIGNAL_CDS_IPS.toSet(),
        BuildConfig.SIGNAL_KEY_BACKUP_URL.stripProtocol() to BuildConfig.SIGNAL_KBS_IPS.toSet(),
        BuildConfig.SIGNAL_SFU_URL.stripProtocol() to BuildConfig.SIGNAL_SFU_IPS.toSet(),
        BuildConfig.CONTENT_PROXY_HOST.stripProtocol() to BuildConfig.SIGNAL_CONTENT_PROXY_IPS.toSet(),
      )
    )
  )

  private fun String.stripProtocol(): String {
    return this.removePrefix("https://")
  }
}
