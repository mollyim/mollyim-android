package org.thoughtcrime.securesms.net;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;

import java.util.Arrays;
import java.util.Objects;

import info.guardianproject.netcipher.proxy.OrbotHelper;

public class NetworkManager {

  private static final String TAG = Log.tag(NetworkManager.class);

  private final ApplicationContext application;
  private final OrbotHelper        orbotHelper;

  private OrbotStatusCallback orbotStatusCallback;

  private ProxyType proxyType;

  private String proxySocksHost;
  private int proxySocksPort;
  private int proxyOrbotPort;

  private SocksProxy existingProxy;

  private NetworkManager(@NonNull ApplicationContext application,
                         @NonNull OrbotHelper orbotHelper) {
    this.application = application;
    this.orbotHelper = orbotHelper;
    this.proxyType   = ProxyType.NONE;
  }

  @NonNull
  public static NetworkManager create(@NonNull Context context) {
    return new NetworkManager(ApplicationContext.getInstance(context), OrbotHelper.get(context));
  }

  public boolean isOrbotAvailable() {
    return orbotHelper.init();
  }

  public synchronized void setProxyChoice(ProxyType type) {
    if (orbotStatusCallback != null) {
      orbotHelper.removeStatusCallback(orbotStatusCallback);
      orbotStatusCallback = null;
    }

    proxyOrbotPort = SocksProxy.INVALID_PORT;
    proxyType = type;

    if (type == ProxyType.ORBOT && isOrbotAvailable()) {
      orbotStatusCallback = new OrbotStatusCallback();
      orbotHelper.addStatusCallback(orbotStatusCallback);
      orbotHelper.requestStart(application);
    }
  }

  public void setProxySocksPort(int port) {
    proxySocksPort = port;
  }

  public void setProxySocksHost(String host) {
    proxySocksHost = host;
  }

  public String getDefaultProxySocksHost() {
    return OrbotHelper.DEFAULT_PROXY_HOST;
  }

  public int getDefaultProxySocksPort() {
    return OrbotHelper.DEFAULT_PROXY_SOCKS_PORT;
  }

  public void applyConfiguration() {
    SocksProxy newProxy;

    if (proxyType == ProxyType.SOCKS5) {
      newProxy = new SocksProxy(proxySocksHost, proxySocksPort);
    } else if (proxyType == ProxyType.ORBOT) {
      newProxy = new SocksProxy(OrbotHelper.DEFAULT_PROXY_HOST, proxyOrbotPort);
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

  final class OrbotStatusCallback extends OrbotHelper.SimpleStatusCallback {

    @Override
    public void onEnabled(Intent statusIntent) {
      Log.i(TAG, "[OrbotHelper] onEnabled");
      int socksPort = getSocksPort(statusIntent);
      synchronized (orbotHelper) {
        if (proxyOrbotPort != socksPort) {
          if (proxyType == ProxyType.ORBOT) {
            configureNetwork(new SocksProxy(OrbotHelper.DEFAULT_PROXY_HOST, socksPort));
          }
          proxyOrbotPort = socksPort;
          if (application.isAppVisible()) {
            Toast.makeText(application, R.string.ProxyManager_successfully_started_orbot, Toast.LENGTH_SHORT).show();
          }
        }
      }
      orbotHelper.removeStatusCallback(this);
    }

    @Override
    public void onStatusTimeout() {
      Log.i(TAG, "[OrbotHelper] onStatusTimeout");
      if (isOrbotAvailable()) {
        orbotHelper.requestStart(application);
      } else {
        orbotHelper.removeStatusCallback(this);
      }
    }

    private int getSocksPort(Intent status) {
      return status.getIntExtra(OrbotHelper.EXTRA_PROXY_PORT_SOCKS,
                                OrbotHelper.DEFAULT_PROXY_SOCKS_PORT);
    }
  }
}
