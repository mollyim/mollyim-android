package com.google.android.gms.common;

import androidx.annotation.Keep;

import static com.google.android.gms.common.GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE;

@Keep
public class GooglePlayServicesManifestException extends IllegalStateException {

  private final int actualVersion;

  public GooglePlayServicesManifestException(int actualVersion, String message) {
    super(message);
    this.actualVersion = actualVersion;
  }

  public int getActualVersion() {
    return actualVersion;
  }

  public int getExpectedVersion() {
    return GOOGLE_PLAY_SERVICES_VERSION_CODE;
  }
}
