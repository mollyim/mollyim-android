package org.signal.core.util.crypto

import android.os.Build
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles encryption and decryption of String data using AES/GCM/NoPadding.
 * It relies on a [SecretKey] typically obtained from [AndroidKeyGuardian].
 *
 * GCM (Galois/Counter Mode) is chosen because it's an authenticated encryption mode,
 * providing both confidentiality (encryption) and authenticity (MAC). It also handles
 * its own padding, so "NoPadding" is specified.
 *
 * It is CRITICAL that a unique Initialization Vector (IV) is used for every encryption
 * operation with the same key. Reusing IVs with GCM can compromise security severely.
 */
object AndroidDataCipher {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12 // Recommended for GCM (96 bits)
    private const val GCM_TAG_LENGTH_BITS = 128 // Recommended for GCM (128 bits)
    private const val TAG = "AndroidDataCipher"

    /**
     * Encrypts the given plaintext string.
     *
     * @param plaintext The string to encrypt.
     * @param secretKey The [SecretKey] to use for encryption.
     * @return An [EncryptedData] object containing the ciphertext and IV.
     * @throws CryptoException if encryption fails for any reason.
     */
    @Throws(CryptoException::class)
    fun encrypt(plaintext: String, secretKey: SecretKey): EncryptedData {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            // Generate a unique, cryptographically secure IV for each encryption.
            // Reusing IVs with GCM is catastrophic for security.
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)

            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            return EncryptedData(ciphertext, iv)
        } catch (e: Exception) {
            throw CryptoException("Encryption failed.", e)
        }
    }

    /**
     * Decrypts the given [EncryptedData].
     *
     * @param encryptedData The data to decrypt.
     * @param secretKey The [SecretKey] to use for decryption.
     * @return The decrypted plaintext string, or null if decryption fails (e.g., MAC check error,
     *         incorrect key).
     * @throws DecryptionFailedException if decryption encounters an error it cannot recover from,
     *         especially if user authentication is required and fails.
     */
    fun decrypt(encryptedData: EncryptedData, secretKey: SecretKey): String? {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            val decryptedBytes = cipher.doFinal(encryptedData.ciphertext)
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: UserNotAuthenticatedException) {
            // This exception occurs if the key requires user authentication (configured in AndroidKeyGuardian)
            // and it was not performed or has expired. This requires API Level 23 (Marshmallow).
            // The caller should handle this, possibly by re-triggering authentication.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.w(TAG, "Decryption failed: User not authenticated. App should prompt for authentication.", e)
                throw DecryptionFailedException("Decryption failed due to user not being authenticated. Please authenticate to proceed.", e)
            } else {
                // This case should ideally not be reached if user authentication was set,
                // as that itself requires API 23+.
                Log.e(TAG, "UserNotAuthenticatedException on pre-M device, this is unexpected.", e)
                return null
            }
        } catch (e: Exception) {
            // Other decryption errors (e.g., AEADBadTagException for MAC mismatch, incorrect key).
            // For security reasons, common decryption failures (like a bad MAC tag) should
            // return null rather than throwing a detailed exception that might leak information.
            Log.w(TAG, "Decryption failed (e.g., MAC mismatch, wrong key, etc.). Returning null.", e)
            return null
        }
    }
}
