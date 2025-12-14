package com.google.android.gms.tasks;

import androidx.annotation.Keep;

@Keep
public interface OnCompleteListener<TResult> {
  void onComplete(Task<TResult> task);
}
