package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;

import java.util.Arrays;
import java.util.Objects;

public class NetworkManager {

  private static final String TAG = Log.tag(NetworkManager.class);

  private ProxyType proxyType;

  private String proxySocksHost;
  private int proxySocksPort;

  private SocksProxy existingProxy;

  private NetworkManager() {
    this.proxyType      = ProxyType.NONE;
    this.proxySocksHost = SocksProxy.LOCAL_HOST;
    this.proxySocksPort = SocksProxy.INVALID_PORT;
  }

  @NonNull
  public static NetworkManager create() {
    return new NetworkManager();
  }

  public synchronized void setProxyChoice(ProxyType type) {
    proxyType = type;
  }

  public void setProxySocksPort(int port) {
    proxySocksPort = port;
  }

  public void setProxySocksHost(String host) {
    proxySocksHost = host;
  }

  public String getDefaultProxySocksHost() {
    return SocksProxy.LOCAL_HOST;
  }

  public int getDefaultProxySocksPort() {
    return 9050;
  }

  public void applyConfiguration() {
    SocksProxy newProxy;

    if (proxyType == ProxyType.SOCKS5) {
      newProxy = new SocksProxy(proxySocksHost, proxySocksPort);
    } else {
      newProxy = null;
    }

    configureNetwork(newProxy);
  }

  private synchronized void configureNetwork(SocksProxy newProxy) {
    if (!hasProxyChanged(existingProxy, newProxy)) {
      return;
    }

    Network.setSocksProxy(newProxy);

    if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      if (newProxy != null) {
        String newProxyUrl = newProxy.getUrl();
        if (newProxyUrl == null) {
          newProxyUrl = "socks://proxy.invalid";
        }
        final ProxyConfig proxyConfig = new ProxyConfig.Builder()
                                                       .addProxyRule(newProxyUrl)
                                                       .build();
        ProxyController.getInstance().setProxyOverride(proxyConfig, Runnable::run, this::onProxyOverrideComplete);
      } else {
        ProxyController.getInstance().clearProxyOverride(Runnable::run, this::onProxyOverrideComplete);
      }
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      for (SignalServiceMessagePipe pipe : Arrays.asList(
          IncomingMessageObserver.getPipe(),
          IncomingMessageObserver.getUnidentifiedPipe())) {
        if (pipe != null) {
          pipe.shutdown();
        }
      }
    });

    existingProxy = newProxy;
  }

  private void onProxyOverrideComplete() {
    Log.d(TAG, "onProxyOverrideComplete");
  }

  static private boolean hasProxyChanged(SocksProxy existingProxy, SocksProxy newProxy) {
    return !Objects.equals(existingProxy, newProxy);
  }
}
