package com.google.android.gms.common.util;

import android.util.Base64;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public final class Base64Utils {

  private Base64Utils() {}

  @NonNull
  public static String encode(@NonNull byte[] data) {
    return Base64.encodeToString(data, Base64.DEFAULT);
  }

  @NonNull
  public static String encodeUrlSafe(@NonNull byte[] data) {
    return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP);
  }

  @NonNull
  public static String encodeUrlSafeNoPadding(@NonNull byte[] data) {
    return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
  }

  @NonNull
  public static byte[] decode(@NonNull String encodedData) {
    return Base64.decode(encodedData, Base64.DEFAULT);
  }

  @NonNull
  public static byte[] decodeUrlSafe(@NonNull String encodedData) {
    return Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP);
  }

  @NonNull
  public static byte[] decodeUrlSafeNoPadding(@NonNull String encodedData) {
    return Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
  }
}
