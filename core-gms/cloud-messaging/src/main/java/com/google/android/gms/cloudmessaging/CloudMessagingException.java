package com.google.android.gms.cloudmessaging;

import androidx.annotation.Nullable;

final class CloudMessagingException extends Exception {

  public CloudMessagingException(@Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
  }

  public CloudMessagingException(@Nullable String message) {
    super(message);
  }
}
