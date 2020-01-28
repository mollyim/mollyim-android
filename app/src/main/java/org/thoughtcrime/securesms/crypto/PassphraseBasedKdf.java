package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.argon2.Argon2;
import org.signal.argon2.Argon2Exception;
import org.signal.argon2.MemoryCost;
import org.signal.argon2.Type;
import org.signal.argon2.Version;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class PassphraseBasedKdf {

  // Requested time cost in milliseconds
  private static final long ARGON2_TIME_COST = 3000;

  // Defaults for low-end devices
  private static final int ARGON2_MINIMUM_ITERATIONS = 4;
  private static final int ARGON2_MINIMUM_MEMORY     = 16383;

  private SecretKey    secretKey;
  private Params       params;
  private long         elapsedMillis;

  public void setSecretKey(SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public SecretKey getSecretKey() {
    return secretKey;
  }

  public String getParameters() {
    return params.serialize();
  }

  public long getElapsedTimeMillis() {
    return elapsedMillis;
  }

  public void setParameters(String serialized) {
    this.params = Params.deserialize(serialized);
  }

  public void findParameters(long maxMemory) {
    Argon2Benchmark benchmark = new Argon2Benchmark(ARGON2_MINIMUM_ITERATIONS,
                                                    ARGON2_MINIMUM_MEMORY, maxMemory);

    try {
      benchmark.run(ARGON2_TIME_COST);
    } catch (Argon2Exception e) {
      throw new AssertionError(e);
    }

    params = Params.createFromBenchmark(benchmark);
    elapsedMillis = benchmark.getElapsedTime();
  }

  public SecureSecretKeySpec deriveKey(char[] passphrase, byte[] salt) {
    byte[] pwd = passwordToBytes(passphrase);

    try {
      byte[] hash = new Argon2.Builder(Version.V13)
                              .type(Type.Argon2id)
                              .parallelism(params.getParallelism())
                              .iterations(params.getIterations())
                              .memoryCost(params.getMemoryCost())
                              .hashRaw(true)
                              .build()
                              .hash(pwd, salt)
                              .getHash();

      if (secretKey != null) {
        return new SecureSecretKeySpec(hmac(hash, secretKey), "AES");
      } else {
        return new SecureSecretKeySpec(hash, "AES");
      }
    } catch (Argon2Exception e) {
      throw new AssertionError(e);
    } finally {
      Arrays.fill(pwd, (byte) 0);
    }
  }

  public static byte[] passwordToBytes(final char[] password) {
    return PKCS12PasswordToBytes(password);
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

  private static byte[] hmac(byte[] input, SecretKey key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return mac.doFinal(input);
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }

  public static class Params {
    @JsonProperty(value = "t")
    private int iterations;

    @JsonProperty(value = "m")
    private long memory;

    @JsonProperty(value = "p")
    private int parallelism;

    private static Params createFromBenchmark(Argon2Benchmark benchmark) {
      return new Params(benchmark.getIterations(), benchmark.getMemory(), benchmark.getParallelism());
    }

    public Params() {}

    public Params(int iterations, long memory, int parallelism) {
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
    public MemoryCost getMemoryCost() {
      return MemoryCost.Bytes(memory);
    }

    public int getParallelism() {
      return parallelism;
    }

    public String serialize() {
      try {
        return JsonUtils.toJson(this);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public static Params deserialize(@NonNull String serialized) {
      try {
        return JsonUtils.fromJson(serialized, Params.class);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

  }
}
