package com.google.android.gms.tasks;

import androidx.annotation.Keep;

@Keep
public class RuntimeExecutionException extends RuntimeException {
  public RuntimeExecutionException(Throwable cause) {
    super(cause);
  }
}
