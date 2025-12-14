package com.google.android.gms.tasks;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public interface OnFailureListener {
  void onFailure(@NonNull Exception e);
}
