package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

public class ProxySocketFactory extends SocketFactory {

  final @NonNull ProxyProvider proxyProvider;

  public ProxySocketFactory(@NonNull ProxyProvider proxyProvider) {
    this.proxyProvider = proxyProvider;
  }

  @Override
  public Socket createSocket() throws IOException {
    return new Socket(proxyProvider.getProxy());
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    throw new UnsupportedOperationException();
  }
}
