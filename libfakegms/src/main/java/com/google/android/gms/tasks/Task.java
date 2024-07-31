package com.google.android.gms.tasks;

public class Task<TResult> {
  public boolean isSuccessful() {
    return false;
  }

  public TResult getResult() {
    return null;
  }

  public <X extends Throwable> TResult getResult(Class<X> clazz) throws X {
    return null;
  }

  public Exception getException() {
    return new UnsupportedOperationException();
  }

  public Task<TResult> addOnSuccessListener(OnSuccessListener<TResult> listener) {
    return this;
  }

  public Task<TResult> addOnFailureListener(OnFailureListener<TResult> listener) {
    listener.onFailure(getException());
    return this;
  }

  public Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> listener) {
    listener.onComplete(this);
    return this;
  }

  public Task<TResult> addOnCanceledListener(OnCanceledListener listener) {
    listener.onCanceled();
    return this;
  }
}
