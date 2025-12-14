package com.google.android.gms.tasks;

import android.app.Activity;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

@Keep
public abstract class Task<TResult> {

  @NonNull
  public Task<TResult> addOnCanceledListener(@NonNull OnCanceledListener listener) {
    throw new UnsupportedOperationException("addOnCanceledListener is not implemented.");
  }

  @NonNull
  public Task<TResult> addOnCanceledListener(@NonNull Activity activity, @NonNull OnCanceledListener listener) {
    throw new UnsupportedOperationException("addOnCanceledListener is not implemented.");
  }

  @NonNull
  public Task<TResult> addOnCanceledListener(@NonNull Executor executor, @NonNull OnCanceledListener listener) {
    throw new UnsupportedOperationException("addOnCanceledListener is not implemented.");
  }

  @NonNull
  public Task<TResult> addOnCompleteListener(@NonNull OnCompleteListener<TResult> listener) {
    throw new UnsupportedOperationException("addOnCompleteListener is not implemented");
  }


  @NonNull
  public Task<TResult> addOnCompleteListener(@NonNull Activity activity, @NonNull OnCompleteListener<TResult> listener) {
    throw new UnsupportedOperationException("addOnCompleteListener is not implemented");
  }

  @NonNull
  public Task<TResult> addOnCompleteListener(@NonNull Executor executor, @NonNull OnCompleteListener<TResult> listener) {
    throw new UnsupportedOperationException("addOnCompleteListener is not implemented");
  }

  @NonNull
  public abstract Task<TResult> addOnFailureListener(@NonNull OnFailureListener listener);

  @NonNull
  public abstract Task<TResult> addOnFailureListener(@NonNull Activity activity, @NonNull OnFailureListener listener);

  @NonNull
  public abstract Task<TResult> addOnFailureListener(@NonNull Executor executor, @NonNull OnFailureListener listener);

  @NonNull
  public abstract Task<TResult> addOnSuccessListener(@NonNull OnSuccessListener<? super TResult> listener);

  @NonNull
  public abstract Task<TResult> addOnSuccessListener(@NonNull Activity activity, @NonNull OnSuccessListener<? super TResult> listener);

  @NonNull
  public abstract Task<TResult> addOnSuccessListener(@NonNull Executor executor, @NonNull OnSuccessListener<? super TResult> listener);

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> continueWith(@NonNull Continuation<TResult, TContinuationResult> continuation) {
    throw new UnsupportedOperationException("continueWith is not implemented");
  }

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> continueWith(@NonNull Executor executor, @NonNull Continuation<TResult, TContinuationResult> continuation) {
    throw new UnsupportedOperationException("continueWith is not implemented");
  }

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(@NonNull Continuation<TResult, Task<TContinuationResult>> continuation) {
    throw new UnsupportedOperationException("continueWithTask is not implemented");
  }

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(@NonNull Executor executor, @NonNull Continuation<TResult, Task<TContinuationResult>> continuation) {
    throw new UnsupportedOperationException("continueWithTask is not implemented");
  }

  public abstract @Nullable Exception getException();

  public abstract TResult getResult();

  public abstract <X extends Throwable> TResult getResult(@NonNull Class<X> exceptionType) throws X;

  public abstract boolean isCanceled();

  public abstract boolean isComplete();

  public abstract boolean isSuccessful();

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(@NonNull SuccessContinuation<TResult, TContinuationResult> successContinuation) {
    throw new UnsupportedOperationException("onSuccessTask is not implemented");
  }

  @NonNull
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(@NonNull Executor executor, @NonNull SuccessContinuation<TResult, TContinuationResult> successContinuation) {
    throw new UnsupportedOperationException("onSuccessTask is not implemented");
  }
}
