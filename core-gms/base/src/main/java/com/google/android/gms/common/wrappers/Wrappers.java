package com.google.android.gms.common.wrappers;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class Wrappers {

  private static volatile PackageManagerWrapper packageManagerWrapper;

  private Wrappers() {}

  public static PackageManagerWrapper packageManager(@NonNull Context context) {
    PackageManagerWrapper wrapper = packageManagerWrapper;
    if (wrapper == null) {
      synchronized (Wrappers.class) {
        wrapper = packageManagerWrapper;
        if (wrapper == null) {
          wrapper = new PackageManagerWrapper(context);
          packageManagerWrapper = wrapper;
        }
      }
    }
    return wrapper;
  }
}
