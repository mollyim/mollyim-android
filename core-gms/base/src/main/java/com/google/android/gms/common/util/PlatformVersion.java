package com.google.android.gms.common.util;

import android.os.Build.VERSION;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.Keep;
import androidx.core.os.BuildCompat;

@Keep
public final class PlatformVersion {

  private PlatformVersion() {}

  @ChecksSdkIntAtLeast(api = 11)
  public static boolean isAtLeastHoneycomb() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 12)
  public static boolean isAtLeastHoneycombMR1() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 14)
  public static boolean isAtLeastIceCreamSandwich() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 15)
  public static boolean isAtLeastIceCreamSandwichMR1() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 16)
  public static boolean isAtLeastJellyBean() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 17)
  public static boolean isAtLeastJellyBeanMR1() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 18)
  public static boolean isAtLeastJellyBeanMR2() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 19)
  public static boolean isAtLeastKitKat() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 20)
  public static boolean isAtLeastKitKatWatch() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 21)
  public static boolean isAtLeastLollipop() {
    return true;
  }

  @ChecksSdkIntAtLeast(api = 22)
  public static boolean isAtLeastLollipopMR1() {
    return VERSION.SDK_INT >= 22;
  }

  @ChecksSdkIntAtLeast(api = 23)
  public static boolean isAtLeastM() {
    return VERSION.SDK_INT >= 23;
  }

  @ChecksSdkIntAtLeast(api = 24)
  public static boolean isAtLeastN() {
    return VERSION.SDK_INT >= 24;
  }

  @ChecksSdkIntAtLeast(api = 26)
  public static boolean isAtLeastO() {
    return VERSION.SDK_INT >= 26;
  }

  @ChecksSdkIntAtLeast(api = 28)
  public static boolean isAtLeastP() {
    return VERSION.SDK_INT >= 28;
  }

  @ChecksSdkIntAtLeast(api = 29)
  public static boolean isAtLeastQ() {
    return VERSION.SDK_INT >= 29;
  }

  @ChecksSdkIntAtLeast(api = 30)
  public static boolean isAtLeastR() {
    return VERSION.SDK_INT >= 30;
  }

  @ChecksSdkIntAtLeast(api = 31)
  public static boolean isAtLeastS() {
    return VERSION.SDK_INT >= 31;
  }

  @ChecksSdkIntAtLeast(api = 32)
  public static boolean isAtLeastSv2() {
    return VERSION.SDK_INT >= 32;
  }

  @ChecksSdkIntAtLeast(api = 33)
  public static boolean isAtLeastT() {
    return VERSION.SDK_INT >= 33;
  }

  @ChecksSdkIntAtLeast(api = 34)
  public static boolean isAtLeastU() {
    return VERSION.SDK_INT >= 34;
  }

  @ChecksSdkIntAtLeast(api = 35)
  public static boolean isAtLeastV() {
    return BuildCompat.isAtLeastV();
  }
}
