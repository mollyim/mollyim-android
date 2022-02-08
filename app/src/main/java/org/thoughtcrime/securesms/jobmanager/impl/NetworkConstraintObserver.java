package org.thoughtcrime.securesms.jobmanager.impl;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.NetworkAvailableEvent;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NetworkConstraintObserver implements ConstraintObserver {

  private static final String REASON = Log.tag(NetworkConstraintObserver.class);

  private final Application application;

  private volatile Notifier notifier;
  // MOLLY: Apply additional constraint on hasInternet() controlled by NetworkManager
  private volatile boolean  connected;

  private final Set<NetworkListener> networkListeners = new HashSet<>();

  private static volatile NetworkConstraintObserver instance;

  public static NetworkConstraintObserver getInstance(@NonNull Application application) {
    if (instance == null) {
      synchronized (NetworkConstraintObserver.class) {
        if (instance == null) {
          instance = new NetworkConstraintObserver(application);
        }
      }
    }
    return instance;
  }

  private NetworkConstraintObserver(Application application) {
    this.application = application;
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier = notifier;
    requestNetwork(0);
    EventBus.getDefault().register(this);
  }

  @TargetApi(19)
  private static boolean isActiveNetworkConnected(@NonNull Context context) {
    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  private void requestNetwork(int retryCount) {
    if (Build.VERSION.SDK_INT < 24 || retryCount > 5) {
      triggerOnNetworkChanged(isActiveNetworkConnected(application));

      application.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          triggerOnNetworkChanged(isActiveNetworkConnected(context));
        }
      }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    } else {
      NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                           .build();

      ConnectivityManager connectivityManager = Objects.requireNonNull(ContextCompat.getSystemService(application, ConnectivityManager.class));
      connectivityManager.requestNetwork(request, Build.VERSION.SDK_INT >= 26 ? new NetworkStateListener26(retryCount) : new NetworkStateListener24());
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onNetworkReadyEvent(@NonNull NetworkAvailableEvent event) {
    triggerOnNetworkChanged(connected);
  }

  private void triggerOnNetworkChanged(boolean connected) {
    this.connected = connected;
    if (hasInternet()) {
      notifier.onConstraintMet(REASON);
    }
    notifyListeners();
  }

  public boolean hasInternet() {
    return connected && ApplicationDependencies.getNetworkManager().isNetworkEnabled();
  }

  public void addListener(@Nullable NetworkListener networkListener) {
    synchronized (networkListeners) {
      networkListeners.add(networkListener);
    }
  }

  public void removeListener(@Nullable NetworkListener networkListener) {
    if (networkListener == null) {
      return;
    }

    synchronized (networkListeners) {
      networkListeners.remove(networkListener);
    }
  }

  private void notifyListeners() {
    synchronized (networkListeners) {
      //noinspection SimplifyStreamApiCallChains
      networkListeners.stream().forEach(NetworkListener::onNetworkChanged);
    }
  }

  @TargetApi(24)
  private class NetworkStateListener24 extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(@NonNull Network network) {
      Log.i(REASON, "Network available: " + network.hashCode());
      triggerOnNetworkChanged(true);
    }

    @Override
    public void onLost(@NonNull Network network) {
      Log.i(REASON, "Network loss: " + network.hashCode());
      triggerOnNetworkChanged(false);
    }
  }

  @TargetApi(26)
  private class NetworkStateListener26 extends NetworkStateListener24 {
    private final int retryCount;

    public NetworkStateListener26(int retryCount) {
      this.retryCount = retryCount;
    }

    @Override
    public void onUnavailable() {
      Log.w(REASON, "No networks available");
      requestNetwork(retryCount + 1);
    }
  }

  public interface NetworkListener {
    void onNetworkChanged();
  }
}
