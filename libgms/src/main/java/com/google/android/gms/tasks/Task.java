package com.google.android.gms.tasks;

public class Task<T> {
  public void addOnCompleteListener(OnCompleteListener listener) {
    listener.onComplete(new Task<TResult>());
  }

  public Task<T> addOnSuccessListener(OnSuccessListener<T> listener) {
    return this;
  }

  public Task<T> addOnFailureListener(OnFailureListener listener) {
    listener.onFailure(new UnsupportedOperationException());
    return this;
  }

  public TResult getResult() {
    return new TResult();
  }

  public boolean isSuccessful() {
    return false;
  }

  public Throwable getException() {
    return new UnsupportedOperationException();
  }
}
