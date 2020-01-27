package org.thoughtcrime.securesms.crypto;

public final class Argon2 {

  static {
    System.loadLibrary("argon2");
  }

  private static native byte[] IDHashRaw(int t_cost, int m_cost, int threads,
                                         byte[] jpwd, byte[] jsalt, int outlen);

  public static byte[] generateHash(Argon2Params p, char[] password, byte[] salt, int numBytes) {
    return IDHashRaw(p.getIterations(), p.getMemoryKB(), p.getParallelism(),
                     p.passwordToBytes(password), salt, numBytes);
  }
}
