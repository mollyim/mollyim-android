package com.google.android.gms.tasks;

public class Task<TResult> {
  public Task<TResult> addOnSuccessListener(OnSuccessListener<TResult> listener) {
    return this;
  }

  public Task<TResult> addOnFailureListener(OnFailureListener<TResult> listener) {
    listener.onFailure(new UnsupportedOperationException());
    return this;
  }

  public TResult getResult() {
    return null;
  }
}
