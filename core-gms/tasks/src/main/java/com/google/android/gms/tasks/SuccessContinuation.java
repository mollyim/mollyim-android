package com.google.android.gms.tasks;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public interface SuccessContinuation<TResult, TContinuationResult> {
  @NonNull
  Task<TContinuationResult> then(TResult result) throws Exception;
}
