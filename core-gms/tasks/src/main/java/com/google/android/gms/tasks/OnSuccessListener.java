package com.google.android.gms.tasks;

import androidx.annotation.Keep;

@Keep
public interface OnSuccessListener<TResult> {
  void onSuccess(TResult result);
}
