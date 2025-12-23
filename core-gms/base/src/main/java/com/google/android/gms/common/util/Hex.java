package com.google.android.gms.common.util;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class Hex {

  private Hex() {}

  @NonNull
  public static String bytesToStringLowercase(@NonNull byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @NonNull
  public static String bytesToStringUppercase(@NonNull byte[] bytes) {
    return bytesToStringUppercase(bytes, false);
  }

  @NonNull
  public static String bytesToStringUppercase(@NonNull byte[] bytes, boolean zeroTerminated) {
    int len = bytes.length;
    StringBuilder sb = new StringBuilder(len * 2);
    for (int i = 0; i < len; i++) {
      if (zeroTerminated && i == len - 1 && bytes[i] == 0) {
        break;
      }
      sb.append(String.format("%02X", bytes[i]));
    }
    return sb.toString();
  }

  @NonNull
  public static byte[] stringToBytes(String hex) throws IllegalArgumentException {
    int len = hex.length();
    if (len % 2 != 0) {
      throw new IllegalArgumentException();
    }
    byte[] bytes = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
    }
    return bytes;
  }
}
