package com.google.android.gms.cloudmessaging;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.android.gms.common.wrappers.PackageManagerWrapper;
import com.google.android.gms.common.wrappers.Wrappers;

import static com.google.android.gms.common.GoogleApiAvailabilityLight.GOOGLE_PLAY_SERVICES_PACKAGE;

final class Metadata {

  private static final String TAG = "Metadata";

  private final PackageManagerWrapper packageManager;

  private Integer gmsPackageVersion;

  public Metadata(Context context) {
    this.packageManager = Wrappers.packageManager(context);
  }

  public int getGmsPackageVersion() {
    if (gmsPackageVersion == null) {
      try {
        PackageInfo packageInfo = packageManager.getPackageInfo(GOOGLE_PLAY_SERVICES_PACKAGE, 0);
        gmsPackageVersion = packageInfo.versionCode;
      } catch (PackageManager.NameNotFoundException e) {
        Log.w(TAG, "Failed to find package: " + e);
        return -1;
      }
    }
    return gmsPackageVersion;
  }
}
