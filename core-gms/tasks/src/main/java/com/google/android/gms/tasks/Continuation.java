package com.google.android.gms.tasks;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public interface Continuation<TResult, TContinuationResult> {
  TContinuationResult then(@NonNull Task<TResult> task) throws Exception;
}
