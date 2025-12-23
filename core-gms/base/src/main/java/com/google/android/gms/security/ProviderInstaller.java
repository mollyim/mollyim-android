package com.google.android.gms.security;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

@Keep
public class ProviderInstaller {

  public static void installIfNeeded(Context context) throws GooglePlayServicesRepairableException, GooglePlayServicesNotAvailableException {
    // NO-OP
  }

  public static void installIfNeededAsync(Context context, ProviderInstaller.ProviderInstallListener listener) {
    // NO-OP
  }

  public interface ProviderInstallListener {
    void onProviderInstallFailed(int errorCode, @Nullable Intent recoveryIntent);

    void onProviderInstalled();
  }
}
