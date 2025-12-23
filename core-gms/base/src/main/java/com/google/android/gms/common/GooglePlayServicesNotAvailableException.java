package com.google.android.gms.common;

import androidx.annotation.Keep;

@Keep
public final class GooglePlayServicesNotAvailableException extends Exception {

  public final int errorCode;

  public GooglePlayServicesNotAvailableException(int errorCode) {
    this.errorCode = errorCode;
  }
}
