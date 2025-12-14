package com.google.android.gms.common;

import android.content.Intent;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class UserRecoverableException extends Exception {

  private final Intent intent;

  public UserRecoverableException(@NonNull String msg, @NonNull Intent intent) {
    super(msg);
    this.intent = intent;
  }

  @NonNull
  public Intent getIntent() {
    return new Intent(intent);
  }
}
