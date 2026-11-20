/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import okhttp3.Dns
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.net.SocketFactory

class ProxyConfigTest {

  companion object {
    private const val TARGET_URL = "https://chat.signal.org"
  }

  @Test
  fun `resolve returns Direct when there is no system proxy`() {
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(Proxy.NO_PROXY))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve maps a loopback (PAC) proxy by hostname`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("localhost", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "localhost", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve maps an unresolved loopback (PAC) proxy by ipv4`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "127.0.0.1", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve maps an unresolved loopback (PAC) proxy by ipv6`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("[::1]", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "[::1]", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve maps a resolved loopback (PAC) proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "127.0.0.1", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve maps a non-loopback numeric system proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("10.0.0.1", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "10.0.0.1", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve maps a manual http system proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "proxy.example.com", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  @Test
  fun `resolve prefers a user proxy over the system proxy`() {
    val user = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050))
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(
      userProxy = user,
      configuration = configuration(censored = false),
      targetUrl = TARGET_URL,
      proxySelector = StubProxySelector(system)
    )
    assertThat(config).isEqualTo(
      ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.SOCKS_REMOTE_DNS, "127.0.0.1", 9050, ProxyConfig.ProxySource.USER)
    )
  }

  @Test
  fun `resolve maps a user http proxy`() {
    val user = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(
      userProxy = user,
      configuration = configuration(censored = false),
      targetUrl = TARGET_URL,
      proxySelector = StubProxySelector(Proxy.NO_PROXY)
    )
    assertThat(config).isEqualTo(
      ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "proxy.example.com", 8080, ProxyConfig.ProxySource.USER)
    )
  }

  @Test
  fun `resolve ignores the system proxy when the configuration is censored`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = true), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve uses the system proxy when the configuration is not censored`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(Proxy.NO_PROXY, configuration(censored = false), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "proxy.example.com", 8080, ProxyConfig.ProxySource.SYSTEM))
  }

  private fun configuration(censored: Boolean): SignalServiceConfiguration {
    return SignalServiceConfiguration(
      signalServiceUrls = emptyArray(),
      signalCdnUrlMap = emptyMap(),
      signalStorageUrls = emptyArray(),
      signalCdsiUrls = emptyArray(),
      signalSvr2Urls = emptyArray(),
      networkInterceptors = emptyList(),
      socketFactory = SocketFactory.getDefault(),
      proxySelector = ProxySelector.getDefault(),
      dns = Dns.SYSTEM,
      zkGroupServerPublicParams = ByteArray(0),
      genericServerPublicParams = ByteArray(0),
      backupServerPublicParams = ByteArray(0),
      censored = censored
    )
  }

  private class StubProxySelector(private vararg val proxies: Proxy) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> = proxies.toList()
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
  }
}
