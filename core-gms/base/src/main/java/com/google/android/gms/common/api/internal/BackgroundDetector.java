package com.google.android.gms.common.api.internal;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.util.ProcessUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Keep
public final class BackgroundDetector implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

  private final AtomicBoolean inBackground = new AtomicBoolean();
  private final AtomicBoolean evaluated    = new AtomicBoolean();

  private final List<BackgroundStateChangeListener> listeners = new CopyOnWriteArrayList<>();

  private volatile boolean initialized;

  private BackgroundDetector() {}

  private static final class InstanceHolder {
    private static final BackgroundDetector instance = new BackgroundDetector();
  }

  @NonNull
  public static BackgroundDetector getInstance() {
    return InstanceHolder.instance;
  }

  public static void initialize(@NonNull Application application) {
    BackgroundDetector instance = getInstance();
    if (instance.initialized) {
      return;
    }

    synchronized (instance) {
      if (instance.initialized) {
        return;
      }
      instance.initialized = true;
      application.registerActivityLifecycleCallbacks(instance);
      application.registerComponentCallbacks(instance);
    }
  }

  public boolean readCurrentStateIfPossible(boolean isInBackgroundDefault) {
    if (!evaluated.get()) {
      if (ProcessUtils.isIsolatedProcess()) {
        return isInBackgroundDefault;
      }

      ActivityManager.RunningAppProcessInfo info =
          new ActivityManager.RunningAppProcessInfo();
      ActivityManager.getMyMemoryState(info);

      boolean firstEval = !evaluated.getAndSet(true);
      if (firstEval && info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
        inBackground.set(true);
      }
    }

    return isInBackground();
  }

  public boolean isInBackground() {
    return inBackground.get();
  }

  public void addListener(@NonNull BackgroundStateChangeListener listener) {
    listeners.add(listener);

    if (initialized) {
      listener.onBackgroundStateChanged(inBackground.get());
    }
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    enterForeground();
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {}

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    enterForeground();
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {}

  @Override
  public void onActivityStopped(@NonNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {}

  @Override
  public void onTrimMemory(int level) {
    if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
      enterBackground();
    }
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {}

  @Override
  public void onLowMemory() {}

  private void enterForeground() {
    boolean stateChanged = inBackground.compareAndSet(true, false);
    evaluated.set(true);
    if (stateChanged) {
      notifyListeners(false);
    }
  }

  private void enterBackground() {
    boolean stateChanged = inBackground.compareAndSet(false, true);
    evaluated.set(true);
    if (stateChanged) {
      notifyListeners(true);
    }
  }

  private void notifyListeners(boolean nowBackground) {
    for (BackgroundStateChangeListener listener : listeners) {
      listener.onBackgroundStateChanged(nowBackground);
    }
  }

  public interface BackgroundStateChangeListener {
    void onBackgroundStateChanged(boolean isInBackground);
  }
}
