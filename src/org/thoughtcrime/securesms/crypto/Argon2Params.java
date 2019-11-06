package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;

public class Argon2Params {
  @JsonProperty(value = "t")
  private int iterations;

  @JsonProperty(value = "m")
  private long memory;

  @JsonProperty(value = "p")
  private int parallelism;

  public Argon2Params() {}

  public Argon2Params(int iterations, long memory) {
    this(iterations, memory, Runtime.getRuntime().availableProcessors());
  }

  public Argon2Params(int iterations, long memory, int parallelism) {
    this.iterations  = iterations;
    this.memory      = memory;
    this.parallelism = parallelism;
  }

  public int getIterations() {
    return iterations;
  }

  public long getMemory() {
    return memory;
  }

  @JsonIgnore
  public int getMemoryKB() {
    return (int) (memory / 1024);
  }

  public int getParallelism() {
    return parallelism;
  }

  public byte[] passwordToBytes(final char[] password) {
    return PKCS12PasswordToBytes(password);
  }

  public String serialize() {
    try {
      return JsonUtils.toJson(this);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static Argon2Params deserialize(@NonNull String serialized) {
    try {
      return JsonUtils.fromJson(serialized, Argon2Params.class);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] PKCS12PasswordToBytes(final char[] password) {
    if (password != null && password.length > 0) {
      // +1 for extra 2 pad bytes.
      byte[] bytes = new byte[(password.length + 1) * 2];

      for (int i = 0; i < password.length; i++) {
        bytes[i * 2] = (byte)(password[i] >>> 8);
        bytes[i * 2 + 1] = (byte)password[i];
      }

      return bytes;
    } else {
      return new byte[0];
    }
  }
}
