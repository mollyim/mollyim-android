package com.google.android.gms.common.wrappers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

@Keep
public class PackageManagerWrapper {

  private final PackageManager packageManager;
  private final Context        appContext;

  public PackageManagerWrapper(@NonNull Context context) {
    this.packageManager = context.getPackageManager();
    this.appContext     = context.getApplicationContext();
  }

  public int checkCallingOrSelfPermission(@NonNull String permission) {
    return appContext.checkCallingOrSelfPermission(permission);
  }

  public int checkPermission(@NonNull String permission, @NonNull String packageName) {
    return packageManager.checkPermission(permission, packageName);
  }

  public boolean isCallerInstantApp() {
    return false;
  }

  public boolean hasSystemFeature(@NonNull String featureName) {
    return packageManager.hasSystemFeature(featureName);
  }

  @NonNull
  public ApplicationInfo getApplicationInfo(@NonNull String packageName, int flags)
      throws PackageManager.NameNotFoundException
  {
    return packageManager.getApplicationInfo(packageName, flags);
  }

  @NonNull
  public PackageInfo getPackageInfo(@NonNull String packageName, int flags)
      throws PackageManager.NameNotFoundException
  {
    return packageManager.getPackageInfo(packageName, flags);
  }

  @NonNull
  public Pair<CharSequence, Drawable> getApplicationLabelAndIcon(@NonNull String packageName)
      throws PackageManager.NameNotFoundException
  {
    ApplicationInfo info  = getApplicationInfo(packageName, 0);
    CharSequence    label = packageManager.getApplicationLabel(info);
    Drawable        icon  = packageManager.getApplicationIcon(info);
    return Pair.create(label, icon);
  }

  @NonNull
  public CharSequence getApplicationLabel(@NonNull String packageName)
      throws PackageManager.NameNotFoundException
  {
    ApplicationInfo info = getApplicationInfo(packageName, 0);
    return packageManager.getApplicationLabel(info);
  }

  @NonNull
  public PackageInstaller getPackageInstaller() {
    return packageManager.getPackageInstaller();
  }
}
