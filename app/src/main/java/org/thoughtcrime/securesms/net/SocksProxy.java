package org.thoughtcrime.securesms.net;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class SocksProxy {

  public static final int LOCAL_PORT = -1;

  public static final String LOCAL_HOST = "localhost";

  private final String host;
  private final int port;

  public SocksProxy() {
    this(LOCAL_HOST, LOCAL_PORT);
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

  public InetSocketAddress getSocketAddress() {
    InetSocketAddress address = null;
    try {
      address = new InetSocketAddress(host, port);
    } catch (IllegalArgumentException ignored) { }
    return address;
  }

  public Proxy makeProxy() {
    InetSocketAddress address = getSocketAddress();
    return address != null ? new Proxy(Proxy.Type.SOCKS, address) : null;
  }
}
