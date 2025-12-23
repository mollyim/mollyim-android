package com.google.android.gms.common;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import static com.google.android.gms.common.GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE;

@Keep
public final class GooglePlayServicesIncorrectManifestValueException extends GooglePlayServicesManifestException {

  public GooglePlayServicesIncorrectManifestValueException(int actualVersion) {
    super(actualVersion, getString(actualVersion));
  }

  @NonNull
  private static String getString(int actualVersion) {
    return "The meta-data tag in your app's AndroidManifest.xml does not have the right value.  " +
           " Expected " + GOOGLE_PLAY_SERVICES_VERSION_CODE + " but found " + actualVersion + ".  " +
           "You must have the following declaration within the <application> element:     " +
           "<meta-data android:name=\"com.google.android.gms.version\" " +
           "android:value=\"@integer/google_play_services_version\" />";
  }
}
