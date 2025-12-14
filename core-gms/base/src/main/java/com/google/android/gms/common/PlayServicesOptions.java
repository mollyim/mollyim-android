package com.google.android.gms.common;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.wrappers.Wrappers;

final class PlayServicesOptions {

  public static final String GMS_VERSION_RESOURCE_NAME  = "com.google.android.gms.version";

  @Nullable
  private static Integer gmsVersion;

  private PlayServicesOptions() {}

  public static int getVersionCodeFromResource(@NonNull Context context) {
    if (gmsVersion == null) {
      try {
        ApplicationInfo appInfo
            = Wrappers.packageManager(context)
                      .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        Bundle metaData = appInfo.metaData;
        gmsVersion = metaData != null ? metaData.getInt(GMS_VERSION_RESOURCE_NAME, 0) : 0;
      } catch (PackageManager.NameNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return gmsVersion;
  }
}
