package com.google.android.gms.common;

import androidx.annotation.Keep;

@Keep
public final class GoogleApiAvailability extends GoogleApiAvailabilityLight {

  private static final class InstanceHolder {
    private static final GoogleApiAvailability instance = new GoogleApiAvailability();
  }

  private GoogleApiAvailability() {}

  public static GoogleApiAvailability getInstance() {
    return InstanceHolder.instance;
  }
}
