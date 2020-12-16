package org.thoughtcrime.securesms.tracing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public final class Tracer {

  public static final class TrackId {
    public static final long DB_LOCK        = -8675309;

    private static final String DB_LOCK_NAME = "Database Lock";
  }

  private static final Tracer INSTANCE = new Tracer();


  public static @NonNull Tracer getInstance() {
    return INSTANCE;
  }

  public void start(@NonNull String methodName) {
  }

  public void start(@NonNull String methodName, long trackId) {
  }

  public void start(@NonNull String methodName, @NonNull String key, @Nullable String value) {
  }

  public void start(@NonNull String methodName, long trackId, @NonNull String key, @Nullable String value) {
  }

  public void start(@NonNull String methodName, @Nullable Map<String, String> values) {
  }

  public void start(@NonNull String methodName, long trackId, @Nullable Map<String, String> values) {
  }

  public void end(@NonNull String methodName) {
  }

  public void end(@NonNull String methodName, long trackId) {
  }

  public @NonNull byte[] serialize() {
    return new byte[0];
  }
}
