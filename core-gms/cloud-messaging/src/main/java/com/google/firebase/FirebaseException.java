package com.google.firebase;

import androidx.annotation.Keep;

@Keep
public class FirebaseException extends Exception {

  @Deprecated
  protected FirebaseException() {}

  public FirebaseException(String message) {
    super(message);
  }

  public FirebaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
