package org.thoughtcrime.securesms.crypto;

import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.security.auth.Destroyable;

/**
 * Replacement for SecretKeySpec that implements Destroyable interface
 * and securely clears secret key from memory.
 */
public class SecureSecretKeySpec implements KeySpec, SecretKey, Destroyable {

    private static final long serialVersionUID = 6577238317307289933L;

    private byte[] key;

    private final String algorithm;

    private final boolean blank;

    public SecureSecretKeySpec(byte[] key, String algorithm) {
        if (key == null || algorithm == null) {
            throw new IllegalArgumentException("Missing argument");
        }
        if (key.length == 0) {
            throw new IllegalArgumentException("Empty key");
        }
        this.key = key.clone();
        this.algorithm = algorithm;
        this.blank = isAllZeros(key);
    }

    public SecureSecretKeySpec(byte[] key, int offset, int len, String algorithm) {
        if (key == null || algorithm == null) {
            throw new IllegalArgumentException("Missing argument");
        }
        if (key.length == 0) {
            throw new IllegalArgumentException("Empty key");
        }
        if (key.length-offset < len) {
            throw new IllegalArgumentException
                    ("Invalid offset/length combination");
        }
        if (len < 0) {
            throw new ArrayIndexOutOfBoundsException("len is negative");
        }
        this.key = new byte[len];
        this.algorithm = algorithm;
        System.arraycopy(key, offset, this.key, 0, len);
        this.blank = isAllZeros(key);
    }

    public String getAlgorithm()
    {
        return this.algorithm;
    }

    public String getFormat()
    {
        return "RAW";
    }

    /**
     * Returns the key material of this secret key.
     *
     * @return the key material. It does not clone the key
     * as the original specification.
     */
    public byte[] getEncoded()
    {
        if (this.isDestroyed()) {
            throw new IllegalStateException("Secret has already been destroyed");
        }
        return this.key;
    }

    public void destroy() {
        if (this.key != null) {
            Arrays.fill(this.key, (byte) 0);
            this.key = null;
        }
    }

    public boolean isDestroyed() {
        if (this.key == null) {
            return true;
        }
        return !this.blank && isAllZeros(this.key);
    }

    private static boolean isAllZeros(byte[] key) {
        int result = 0;
        // Time-constant comparison
        for (byte b : key) {
            result |= b;
        }
        return result == 0;
    }

    public int hashCode()
    {
        int retval = 0;
        for (int i = 1; i < this.key.length; i++) {
            retval += this.key[i] * i;
        }
        return retval ^ this.algorithm.hashCode();
    }

    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SecretKey)) {
            return false;
        }

        String thatAlg = ((SecretKey)obj).getAlgorithm();
        if (!(thatAlg.equalsIgnoreCase(this.algorithm))) {
            return false;
        }

        byte[] thatKey = ((SecretKey)obj).getEncoded();

        return MessageDigest.isEqual(this.key, thatKey);
    }
}
