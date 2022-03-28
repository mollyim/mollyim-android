package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.NetworkAvailableEvent;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

import java.util.HashSet;
import java.util.Set;

public class NetworkConstraintObserver implements ConstraintObserver {

  private static final String REASON = Log.tag(NetworkConstraintObserver.class);
  private static final String TAG    = Log.tag(NetworkConstraintObserver.class);

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
    this.notifier  = notifier;
    this.connected = isActiveNetworkConnected(application);

    requestNetwork();
    EventBus.getDefault().register(this);
  }

  private static boolean isActiveNetworkConnected(@NonNull Context context) {
    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  private void requestNetwork() {
    application.registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        triggerOnNetworkChanged(isActiveNetworkConnected(context));
      }
    }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onNetworkReadyEvent(@NonNull NetworkAvailableEvent event) {
    triggerOnNetworkChanged(connected);
  }

  private void triggerOnNetworkChanged(boolean connected) {
    this.connected = connected;

    if (hasInternet()) {
      Log.i(TAG, logPrefix() + "Network available.");
      notifier.onConstraintMet(REASON);
    } else {
      Log.w(TAG, logPrefix() + "Network unavailable.");
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

  private static String logPrefix() {
    return "[API " + Build.VERSION.SDK_INT + "] ";
  }

  public interface NetworkListener {
    void onNetworkChanged();
  }
}
