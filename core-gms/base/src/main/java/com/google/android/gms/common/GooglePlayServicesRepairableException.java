package com.google.android.gms.common;

import android.content.Intent;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class GooglePlayServicesRepairableException extends UserRecoverableException {

  public final int connectionStatusCode;

  public GooglePlayServicesRepairableException(int connectionStatusCode, @NonNull String msg, @NonNull Intent intent) {
    super(msg, intent);
    this.connectionStatusCode = connectionStatusCode;
  }

  public int getConnectionStatusCode() {
    return connectionStatusCode;
  }
}
