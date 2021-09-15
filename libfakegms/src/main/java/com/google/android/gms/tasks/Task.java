package com.google.android.gms.tasks;

public class Task<TResult> {
  public boolean isSuccessful() {
    return false;
  }

  public TResult getResult() {
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
}
