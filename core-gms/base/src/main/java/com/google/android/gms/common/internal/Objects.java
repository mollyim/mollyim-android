package com.google.android.gms.common.internal;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Keep
public final class Objects {

  private Objects() {}

  public static boolean equal(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equals(b);
  }

  public static int hashCode(Object... objects) {
    return Arrays.hashCode(objects);
  }

  public static boolean checkBundlesEquality(Bundle a, Bundle b) {
    if (a == null || b == null) {
      return a == b;
    }
    if (a.size() != b.size()) {
      return false;
    }
    Set<String> keys = a.keySet();
    if (!keys.containsAll(b.keySet())) {
      return false;
    }
    for (String k : keys) {
      if (!equal(a.get(k), b.get(k))) {
        return false;
      }
    }
    return true;
  }

  public static final class ToStringHelper {

    private final Object       target;
    private final List<String> parts;

    private ToStringHelper(@NonNull Object target) {
      this.target = target;
      this.parts = new ArrayList<>();
    }

    @NonNull
    public ToStringHelper add(@NonNull String name, Object value) {
      parts.add(name + "=" + value);
      return this;
    }

    @Override
    @NonNull
    public String toString() {
      String name = target.getClass().getSimpleName();

      StringBuilder sb = new StringBuilder(name.length() * 2);
      sb.append(name);
      sb.append('{');

      for (int i = 0; i < parts.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(parts.get(i));
      }

      sb.append('}');
      return sb.toString();
    }
  }


  @NonNull
  public static ToStringHelper toStringHelper(@NonNull Object target) {
    return new ToStringHelper(target);
  }
}
