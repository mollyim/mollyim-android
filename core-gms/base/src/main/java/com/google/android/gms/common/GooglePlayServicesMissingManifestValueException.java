package com.google.android.gms.common;

import androidx.annotation.Keep;

@Keep
public final class GooglePlayServicesMissingManifestValueException extends GooglePlayServicesManifestException {

  private static final String ERROR_MESSAGE =
      "A required meta-data tag in your app's AndroidManifest.xml does not exist. " +
      "You must have the following declaration within the <application> element:     " +
      "<meta-data android:name=\"com.google.android.gms.version\" " +
      "android:value=\"@integer/google_play_services_version\" />";

  public GooglePlayServicesMissingManifestValueException() {
    super(0, ERROR_MESSAGE);
  }
}
