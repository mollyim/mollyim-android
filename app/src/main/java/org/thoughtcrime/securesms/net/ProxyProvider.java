package org.thoughtcrime.securesms.net;

import java.io.IOException;
import java.net.Proxy;

public interface ProxyProvider {
  Proxy getProxy() throws IOException;
}
