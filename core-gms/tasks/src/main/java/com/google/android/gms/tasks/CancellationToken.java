package com.google.android.gms.tasks;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public abstract class CancellationToken {

  public abstract boolean isCancellationRequested();

  @NonNull
  public abstract CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener listener);
}
