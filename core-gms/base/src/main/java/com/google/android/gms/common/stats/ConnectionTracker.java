package com.google.android.gms.common.stats;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.common.wrappers.Wrappers;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Keep
public class ConnectionTracker {

  private static final String TAG = "ConnectionTracker";

  private final Map<ServiceConnection, ServiceConnection> tracked = new ConcurrentHashMap<>();

  private ConnectionTracker() {}

  private static final class InstanceHolder {
    private static final ConnectionTracker instance = new ConnectionTracker();
  }

  @NonNull
  public static ConnectionTracker getInstance() {
    return InstanceHolder.instance;
  }

  public boolean bindService(@NonNull Context context,
                             @NonNull Intent intent,
                             @NonNull ServiceConnection conn,
                             int flags)
  {
    return bindInternal(context, context.getClass().getName(), intent, conn, flags);
  }

  public void unbindService(@NonNull Context context, @NonNull ServiceConnection conn) {
    ServiceConnection current = tracked.remove(conn);
    try {
      context.unbindService(Objects.requireNonNullElse(current, conn));
    } catch (IllegalArgumentException ignored) {}
  }

  private boolean bindInternal(@NonNull Context context,
                               @NonNull String callerName,
                               @NonNull Intent intent,
                               @NonNull ServiceConnection conn,
                               int flags)
  {
    ComponentName component = intent.getComponent();
    if (component != null) {
      String pkg = component.getPackageName();
      if (isStoppedPackage(context, pkg)) {
        Log.w(TAG, "Attempted to bind to a service in a STOPPED package.");
        return false;
      }
    }

    ServiceConnection previous = tracked.putIfAbsent(conn, conn);
    if (previous != null && conn != previous) {
      Log.w(TAG, String.format(
          "Duplicate binding with the same ServiceConnection: %s, %s, %s.",
          conn, callerName, intent.getAction())
      );
    }

    boolean bound = false;
    try {
      bound = context.bindService(intent, conn, flags);
    } finally {
      if (!bound) {
        tracked.remove(conn, conn);
      }
    }
    return bound;
  }

  private static boolean isStoppedPackage(Context context, String packageName) {
    try {
      ApplicationInfo ai =
          Wrappers.packageManager(context).getApplicationInfo(packageName, 0);
      return (ai.flags & ApplicationInfo.FLAG_STOPPED) != 0;
    } catch (PackageManager.NameNotFoundException ignored) {
      return false;
    }
  }
}
