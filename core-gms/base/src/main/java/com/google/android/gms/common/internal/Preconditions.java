package com.google.android.gms.common.internal;

import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

@Keep
public class Preconditions {

  private Preconditions() {}

  public static double checkArgumentInRange(double value, double lower, double upper, @NonNull String valueName) {
    if (value < lower) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%f, %f] (too low)", valueName, lower, upper)
      );
    } else if (value > upper) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%f, %f] (too high)", valueName, lower, upper)
      );
    }
    return value;
  }

  public static float checkArgumentInRange(float value, float lower, float upper, @NonNull String valueName) {
    if (value < lower) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%f, %f] (too low)", valueName, lower, upper)
      );
    } else if (value > upper) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%f, %f] (too high)", valueName, lower, upper)
      );
    }
    return value;
  }

  public static int checkArgumentInRange(int value, int lower, int upper, @NonNull String valueName) {
    if (value < lower) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%d, %d] (too low)", valueName, lower, upper)
      );
    } else if (value > upper) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%d, %d] (too high)", valueName, lower, upper)
      );
    }
    return value;
  }

  public static int requireNonZero(int value) {
    if (value == 0) {
      throw new IllegalArgumentException("Value must not be zero");
    }
    return value;
  }

  public static int requireNonZero(int value, Object errorMessage) {
    if (value == 0) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return value;
  }

  public static long checkArgumentInRange(long value, long lower, long upper, @NonNull String valueName) {
    if (value < lower) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%d, %d] (too low)", valueName, lower, upper)
      );
    } else if (value > upper) {
      throw new IllegalArgumentException(
          String.format("%s is out of range of [%d, %d] (too high)", valueName, lower, upper)
      );
    }
    return value;
  }

  public static long requireNonZero(long value) {
    if (value == 0L) {
      throw new IllegalArgumentException("Value must not be zero");
    }
    return value;
  }

  public static long requireNonZero(long value, @NonNull Object errorMessage) {
    if (value == 0L) {
      throw new IllegalArgumentException(errorMessage.toString());
    }
    return value;
  }

  @NonNull
  public static <T> T checkNotNull(@Nullable T reference) {
    Objects.requireNonNull(reference);
    return reference;
  }

  @NonNull
  public static <T> T checkNotNull(@Nullable T reference, @NonNull Object errorMessage) {
    Objects.requireNonNull(reference, String.valueOf(errorMessage));
    return reference;
  }

  @NonNull
  public static String checkNotEmpty(@Nullable String string) {
    if (TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException("String must not be null or empty");
    } else {
      return string;
    }
  }

  @NonNull
  public static String checkNotEmpty(@Nullable String string, @NonNull Object errorMessage) {
    if (TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    } else {
      return string;
    }
  }

  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  public static void checkArgument(boolean expression, @NonNull Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  public static void checkArgument(boolean expression, @NonNull String errorMessage, @NonNull Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessage, errorMessageArgs));
    }
  }

  public static void checkMainThread() {
    checkMainThread("Must be called on the main thread");
  }

  public static void checkMainThread(@NonNull String errorMessage) {
    if (!isOnMainThread()) {
      throw new IllegalStateException(errorMessage);
    }
  }

  public static void checkNotGoogleApiHandlerThread() {
    checkNotGoogleApiHandlerThread("Must not be called on GoogleApiHandler thread.");
  }

  public static void checkNotGoogleApiHandlerThread(@NonNull String errorMessage) {
    Looper looper = Looper.myLooper();
    if (looper != null) {
      String threadName = looper.getThread().getName();
      if ("GoogleApiHandler".equals(threadName)) {
        throw new IllegalStateException(errorMessage);
      }
    }
  }

  public static void checkNotMainThread() {
    checkNotMainThread("Must not be called on the main thread");
  }

  public static void checkNotMainThread(@NonNull String errorMessage) {
    if (isOnMainThread()) {
      throw new IllegalStateException(errorMessage);
    }
  }

  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  public static void checkState(boolean expression, @NonNull Object errorMessage) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  public static void checkState(boolean expression, @NonNull String errorMessage, @NonNull Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(String.format(errorMessage, errorMessageArgs));
    }
  }

  private static boolean isOnMainThread() {
    return Looper.getMainLooper() == Looper.myLooper();
  }
}
