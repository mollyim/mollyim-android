package com.google.android.gms.common;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.wrappers.PackageManagerWrapper;
import com.google.android.gms.common.wrappers.Wrappers;

import java.util.List;

import static com.google.android.gms.common.GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE;
import static com.google.android.gms.common.GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE;

@Keep
public class GooglePlayServicesUtil {

  private static final String TAG = "GooglePlayServicesUtil";

  GooglePlayServicesUtil() {}

  public static int isGooglePlayServicesAvailable(@NonNull Context context) {
    return isGooglePlayServicesAvailable(context, GOOGLE_PLAY_SERVICES_VERSION_CODE);
  }

  public static int isGooglePlayServicesAvailable(@NonNull Context context, int minApkVersion) {
    Preconditions.checkArgument(minApkVersion >= 0, "minApkVersion must be non-negative");

    int manifestVersion = PlayServicesOptions.getVersionCodeFromResource(context);
    if (manifestVersion == 0) {
      throw new GooglePlayServicesMissingManifestValueException();
    }
    if (manifestVersion != GOOGLE_PLAY_SERVICES_VERSION_CODE) {
      throw new GooglePlayServicesIncorrectManifestValueException(manifestVersion);
    }

    final PackageManagerWrapper pm = Wrappers.packageManager(context);

    PackageInfo gmsInfo;
    try {
      gmsInfo = pm.getPackageInfo(GOOGLE_PLAY_SERVICES_PACKAGE, PackageManager.GET_SIGNATURES);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, context.getPackageName() + " requires Google Play services, but they are missing.");
      return ConnectionResult.SERVICE_MISSING;
    }

    if (!GoogleSignatureVerifier.isGooglePublicSignedPackage(gmsInfo)) {
      Log.w(TAG, context.getPackageName() + " requires Google Play services, but their signature is invalid.");
      return ConnectionResult.SERVICE_INVALID;
    }

    if (normalizeVersionCode(gmsInfo.versionCode) < normalizeVersionCode(minApkVersion)) {
      Log.w(TAG, context.getPackageName() + " requires Google Play services version " +
                 minApkVersion + ", but found " + gmsInfo.versionCode);
      return ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
    }

    if (gmsInfo.applicationInfo == null || !gmsInfo.applicationInfo.enabled) {
      return ConnectionResult.SERVICE_DISABLED;
    }

    return ConnectionResult.SUCCESS;
  }

  public static boolean isPlayServicesPossiblyUpdating(@NonNull Context context, int status) {
    return switch (status) {
      case ConnectionResult.SERVICE_UPDATING -> true;
      case ConnectionResult.SERVICE_MISSING -> isUpdatingOrEnabled(context);
      default -> false;
    };
  }

  private static boolean isUpdatingOrEnabled(Context context) {
    final PackageManagerWrapper pm = Wrappers.packageManager(context);

    try {
      List<PackageInstaller.SessionInfo> sessions =
          pm.getPackageInstaller().getAllSessions();

      for (PackageInstaller.SessionInfo session : sessions) {
        if (GOOGLE_PLAY_SERVICES_PACKAGE.equals(session.getAppPackageName())) {
          return true;
        }
      }
    } catch (Exception ignored) {}

    try {
      return pm.getApplicationInfo(GOOGLE_PLAY_SERVICES_PACKAGE, 0).enabled;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  private static int normalizeVersionCode(int versionCode) {
    return versionCode == -1 ? -1 : versionCode / 1000;
  }
}
