package org.thoughtcrime.securesms.net;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.Proxy;

import javax.net.SocketFactory;

import okhttp3.Dns;

public class Network {

  private static SocksProxy socksProxy;

  private static final Dns sequentialDns;
  private static final Dns dnsOverHttps;

  private static final SocketFactory socketFactory;

  static {
    socketFactory = new ProxySocketFactory(getProxyProvider());
    sequentialDns = new SequentialDns(Dns.SYSTEM, new CustomDns("1.1.1.1"));
    dnsOverHttps  = new DohClient("https://1.1.1.1/dns-query", socketFactory);
  }

  public static void setSocksProxy(SocksProxy proxy) {
    socksProxy = proxy;
  }

  static public SocketFactory getSocketFactory() {
    return socketFactory;
  }

  @Nullable
  static public Proxy getProxy() {
    if (socksProxy != null) {
      return socksProxy.makeProxy();
    } else {
      return Proxy.NO_PROXY;
    }
  }

  static public ProxyProvider getProxyProvider() {
    return () -> {
      Proxy proxy = getProxy();
      if (proxy == null) {
          throw new IOException("Proxy address not available yet");
      }
      return proxy;
    };
  }

  static public Dns getDns() {
    return (hostname) -> {
      if (socksProxy == null) {
        return sequentialDns.lookup(hostname);
      } else {
        return dnsOverHttps.lookup(hostname);
      }
    };
  }
}
