package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.SocketFactory;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class DohClient implements Dns {

  private final DnsOverHttps client;

  public DohClient(@NonNull String url, SocketFactory socketFactory) {
    this.client = new DnsOverHttps.Builder()
                                  .url(HttpUrl.get(url))
                                  .client(buildHttpClient(socketFactory))
                                  .includeIPv6(false)
                                  .build();
  }

  @Override
  public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    return client.lookup(hostname);
  }

  static private OkHttpClient buildHttpClient(SocketFactory socketFactory) {
    return new OkHttpClient.Builder()
                           .socketFactory(socketFactory)
                           .cache(null)
                           .build();
  }
}
