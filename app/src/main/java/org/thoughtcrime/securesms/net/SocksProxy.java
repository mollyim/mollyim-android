package org.thoughtcrime.securesms.net;

import androidx.annotation.Nullable;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class SocksProxy {

  public static final int LOCAL_PORT   = -1;
  public static final int INVALID_PORT = -2;

  public static final String LOCAL_HOST = "localhost";

  private final String host;
  private final int port;

  public SocksProxy() {
    this(LOCAL_HOST, INVALID_PORT);
  }

  public SocksProxy(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Nullable
  public String getUrl() {
    String url = null;
    try {
      url = new URI("socks", null, host, port, null, null, null).toString();
    } catch (URISyntaxException ignored) { }
    return url;
  }

  @Nullable
  public InetSocketAddress getSocketAddress() {
    InetSocketAddress address = null;
    try {
      address = new InetSocketAddress(host, port);
    } catch (IllegalArgumentException ignored) { }
    return address;
  }

  public static boolean isValidHost(String host) {
    if (host == null || host.isEmpty()) {
      return false;
    }
    try {
      new URI("socks", host, null, null);
    } catch (URISyntaxException e) {
      return false;
    }
    return true;
  }

  public static boolean isValidPort(int port) {
    return !(port < 0 || port > 0xFFFF);
  }

  public Proxy makeProxy() {
    InetSocketAddress address = getSocketAddress();
    return address != null ? new Proxy(Proxy.Type.SOCKS, address) : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SocksProxy socksProxy = (SocksProxy) o;
    return Objects.equals(host, socksProxy.host) && port == socksProxy.port;
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }
}
