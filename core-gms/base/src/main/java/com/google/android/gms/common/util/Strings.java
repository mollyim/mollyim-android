package com.google.android.gms.common.util;

import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

@Keep
public class Strings {

  private Strings() {}

  @Nullable
  public static String emptyToNull(String string) {
    return TextUtils.isEmpty(string) ? null : string;
  }

  public static boolean isEmptyOrWhitespace(String string) {
    return string == null || string.trim().isEmpty();
  }
}
