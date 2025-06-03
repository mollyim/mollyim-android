package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.hkdf.HKDF; // Assuming this is the correct HKDF implementation from libsignal
import org.signal.libsignal.protocol.hkdf.HKDFv3;


import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security; // Added for Security.getProvider/addProvider
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider; // Added for BouncyCastleProvider

/**
 * Provides Authenticated Encryption with Associated Data (AEAD) using ChaCha20-Poly1305.
 * The encryption key is derived from a shared secret (e.g., from ECDH) and a passphrase
 * using HKDFv3 (SHA-256).
 *
 * Nonces are generated randomly for each encryption and prepended to the ciphertext.
 * This class relies on BouncyCastle as a JCE provider if ChaCha20-Poly1305 is not
 * available in the default Android provider (e.g., on API levels below 28).
 */
public final class ExtraLockCipher {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // HKDF_ALGORITHM is not directly used as libsignal's HKDF is instantiated directly.
    // private static final String HKDF_ALGORITHM = "HkdfSha256";
    private static final String CIPHER_ALGORITHM = "ChaCha20-Poly1305";
    private static final String INFO_STRING = "SignalExtraLockKey";
    private static final int KEY_LENGTH_BYTES = 32; // 256 bits for ChaCha20
    private static final int NONCE_LENGTH_BYTES = 12; // Standard nonce size for ChaCha20Poly1305

    private final SecretKey sessionKey;

    /**
     * Constructs an {@code ExtraLockCipher} instance.
     * The session key for ChaCha20-Poly1305 is derived from the provided shared secret and passphrase
     * using HKDFv3 with SHA-256. The passphrase acts as the salt for HKDF, and a fixed
     * info string ("SignalExtraLockKey") is used for domain separation.
     *
     * @param sharedSecret The input keying material (IKM), typically from an ECDH exchange (32 bytes for X25519).
     * @param passphrase   The passphrase used as salt in the HKDF derivation.
     */
    public ExtraLockCipher(@NonNull byte[] sharedSecret, @NonNull String passphrase) {
        this.sessionKey = deriveSessionKey(sharedSecret, passphrase.getBytes());
    }

    SecretKey deriveSessionKey(@NonNull byte[] ikm, @NonNull byte[] salt) { // Made package-private for testing
        HKDF hkdf = new HKDFv3();
        byte[] derivedKeyMaterial = hkdf.deriveSecrets(ikm, salt, INFO_STRING.getBytes(), KEY_LENGTH_BYTES);
        // The HKDFv3 implementation in libsignal-protocol-java is:
        // deriveSecrets(byte[] outputKeyMaterial, byte[] inputKeyMaterial, byte[] salt, byte[] info)
        // It fills the outputKeyMaterial buffer.
        // So the usage should be:
        // byte[] derivedKeyMaterial = new byte[KEY_LENGTH_BYTES];
        // hkdf.deriveSecrets(derivedKeyMaterial, ikm, salt, INFO_STRING.getBytes());
        // Let's adjust this based on typical HKDF interfaces that return byte[]

        // Re-checking libsignal HKDFv3:
        // The interface org.signal.libsignal.protocol.hkdf.HKDF defines:
        // byte[] deriveSecrets(byte[] inputKeyMaterial, byte[] salt, byte[] info, int outputLength);
        // HKDFv3 implements this. So the original call `hkdf.deriveSecrets(ikm, salt, INFO_STRING.getBytes(), KEY_LENGTH_BYTES);`
        // is actually correct according to the interface.

        return new SecretKeySpec(derivedKeyMaterial, "ChaCha20"); // Algorithm for SecretKeySpec should be base algorithm not with mode/padding
    }

    /**
     * Encrypts the given plaintext using ChaCha20-Poly1305.
     * A unique 12-byte nonce is generated randomly for each encryption operation. This nonce is
     * prepended to the resulting ciphertext.
     *
     * @param plaintext The data to encrypt.
     * @param associatedData Optional associated data to be authenticated but not encrypted. Can be null.
     * @return A byte array containing the 12-byte nonce followed by the ciphertext and authentication tag.
     * @throws NoSuchPaddingException If the requested padding scheme is not available.
     * @throws NoSuchAlgorithmException If ChaCha20-Poly1305 is not available.
     * @throws InvalidKeyException If the derived session key is invalid.
     * @throws javax.crypto.IllegalBlockSizeException If the input data length is incorrect.
     * @throws javax.crypto.BadPaddingException If padding is incorrect during encryption (not typical for AEAD).
     * @throws java.security.InvalidAlgorithmParameterException If nonce or other parameters are invalid.
     */
    public byte[] encrypt(@NonNull byte[] plaintext, @NonNull byte[] associatedData) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, javax.crypto.IllegalBlockSizeException, javax.crypto.BadPaddingException, java.security.InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(nonce); // Use SecureRandom for nonce generation

        ChaCha20ParameterSpec parameterSpec = new ChaCha20ParameterSpec(nonce, 1); // Initial counter = 1
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, parameterSpec);
        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }

        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend nonce to ciphertext for use in decryption
        byte[] nonceAndCiphertext = new byte[NONCE_LENGTH_BYTES + ciphertext.length];
        System.arraycopy(nonce, 0, nonceAndCiphertext, 0, NONCE_LENGTH_BYTES);
        System.arraycopy(ciphertext, 0, nonceAndCiphertext, NONCE_LENGTH_BYTES, ciphertext.length);

        return nonceAndCiphertext;
    }

    /**
     * Decrypts the given data (nonce prepended to ciphertext) using ChaCha20-Poly1305.
     * It expects the first 12 bytes of {@code nonceAndCiphertext} to be the nonce.
     *
     * @param nonceAndCiphertext The data to decrypt, which must start with the 12-byte nonce
     *                           followed by the ciphertext and authentication tag.
     * @param associatedData Optional associated data that was used during encryption. Must match the
     *                       original AAD for decryption to succeed. Can be null if null was used during encryption.
     * @return The original plaintext data.
     * @throws NoSuchPaddingException If the requested padding scheme is not available.
     * @throws NoSuchAlgorithmException If ChaCha20-Poly1305 is not available.
     * @throws InvalidKeyException If the derived session key is invalid.
     * @throws javax.crypto.IllegalBlockSizeException If the input data length is incorrect.
     * @throws javax.crypto.BadPaddingException If decryption fails, often due to an incorrect key or
     *                                          corrupted ciphertext (including tag mismatch, which might
     *                                          manifest as AEADBadTagException).
     * @throws java.security.InvalidAlgorithmParameterException If the nonce format is incorrect or ciphertext too short.
     */
    public byte[] decrypt(@NonNull byte[] nonceAndCiphertext, @NonNull byte[] associatedData) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, javax.crypto.IllegalBlockSizeException, javax.crypto.BadPaddingException, java.security.InvalidAlgorithmParameterException {
        if (nonceAndCiphertext == null || nonceAndCiphertext.length < NONCE_LENGTH_BYTES) {
            throw new InvalidAlgorithmParameterException("Invalid ciphertext length (must include nonce).");
        }

        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        System.arraycopy(nonceAndCiphertext, 0, nonce, 0, NONCE_LENGTH_BYTES);

        byte[] ciphertext = new byte[nonceAndCiphertext.length - NONCE_LENGTH_BYTES];
        System.arraycopy(nonceAndCiphertext, NONCE_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        ChaCha20ParameterSpec parameterSpec = new ChaCha20ParameterSpec(nonce, 1); // Initial counter = 1
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, parameterSpec);
        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }

        return cipher.doFinal(ciphertext);
    }

    // The dummy HKDFv3 class is removed as we are directly using org.signal.libsignal.protocol.hkdf.HKDFv3
}
