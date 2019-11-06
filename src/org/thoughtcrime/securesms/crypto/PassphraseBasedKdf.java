package org.thoughtcrime.securesms.crypto;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class PassphraseBasedKdf {

  // Requested time cost in milliseconds
  private static final long ARGON2_TIME_COST = 3000;

  // Defaults for low-end devices
  private static final int ARGON2_MINIMUM_ITERATIONS = 4;
  private static final int ARGON2_MINIMUM_MEMORY     = 65536;


  private SecretKey    secretKey;
  private Argon2Params params;

  public void setSecretKey(SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public SecretKey getSecretKey() {
    return secretKey;
  }

  public String getParameters() {
    return params.serialize();
  }

  public void setParameters(String serialized) {
    this.params = Argon2Params.deserialize(serialized);
  }

  public void findParameters(long maxMemory) {
    Argon2Benchmark argon2Benchmark = new Argon2Benchmark(ARGON2_MINIMUM_ITERATIONS, ARGON2_MINIMUM_MEMORY, maxMemory);
    params = argon2Benchmark.findParameters(ARGON2_TIME_COST);
  }

  public SecureSecretKeySpec deriveKey(byte[] salt, char[] passphrase) {
    byte[] hash = Argon2.generateHash(params, passphrase, salt, 32);

    return new SecureSecretKeySpec(hmac(hash), "AES");
  }

  private byte[] hmac(byte[] input) {
    if (secretKey == null) {
      return input;
    }

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(secretKey);
      return mac.doFinal(input);
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }
}
