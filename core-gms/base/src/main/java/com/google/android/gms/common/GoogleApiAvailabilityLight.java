package com.google.android.gms.common;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.BuildConfig;

@Keep
public class GoogleApiAvailabilityLight {

  public static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";
  public static final String GOOGLE_PLAY_STORE_PACKAGE    = "com.android.vending";

  public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE = BuildConfig.GMS_VERSION_CODE;

  public int isGooglePlayServicesAvailable(@NonNull Context context) {
    return isGooglePlayServicesAvailable(context, GOOGLE_PLAY_SERVICES_VERSION_CODE);
  }

  public int isGooglePlayServicesAvailable(@NonNull Context context, int minApkVersion) {
    int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context, minApkVersion);

    if (GooglePlayServicesUtil.isPlayServicesPossiblyUpdating(context, status)) {
      return ConnectionResult.SERVICE_UPDATING;
    }

    return status;
  }

  @Nullable
  public PendingIntent getErrorResolutionPendingIntent(@NonNull Context context, int errorCode, int requestCode) {
    Intent intent = getErrorResolutionIntent(errorCode);
    if (intent == null) {
      return null;
    }
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
    );
  }

  @Nullable
  private Intent getErrorResolutionIntent(int errorCode) {
    switch (errorCode) {
      case ConnectionResult.SERVICE_MISSING:
      case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
        return new Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("market://details?id=" + GOOGLE_PLAY_SERVICES_PACKAGE))
            .setPackage(GOOGLE_PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
      case ConnectionResult.SERVICE_DISABLED:
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", GOOGLE_PLAY_SERVICES_PACKAGE, null));
      default:
        return null;
    }
  }
}
