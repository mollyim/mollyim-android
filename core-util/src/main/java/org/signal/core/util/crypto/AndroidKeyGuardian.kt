package org.signal.core.util.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import android.Manifest // Required for USE_BIOMETRIC constant if used directly

/**
 * Manages cryptographic keys within the Android Keystore system.
 *
 * The Android Keystore provides a secure enclave for storing cryptographic keys,
 * making them more difficult to extract from the device. Using hardware-backed
 * keystores (like StrongBox, if available) further enhances security by moving
 * key material and cryptographic operations into a dedicated secure processor.
 */
object AndroidKeyGuardian {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "com.yourapp.extralock.aeskey" // As per requirement
    private const val KEY_SIZE = 256 // AES-256
    private const val AUTH_TIMEOUT_SECONDS = 30 // 30 seconds for user authentication validity
    private const val TAG = "AndroidKeyGuardian"


    /**
     * Retrieves an existing encryption key or creates a new one if it doesn't exist.
     * The key is stored in the Android Keystore.
     *
     * @return The [SecretKey] for encryption/decryption.
     * @throws KeyGenerationFailedException if key generation fails.
     * @throws KeyStoreUnavailableException if the Keystore is not available.
     * @throws KeyRetrievalFailedException if an existing key cannot be retrieved.
     */
    @Throws(KeyGenerationFailedException::class, KeyStoreUnavailableException::class, KeyRetrievalFailedException::class)
    fun getOrCreateEncryptionKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                // Key exists, retrieve it
                try {
                    val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                    if (secretKeyEntry != null) {
                        return secretKeyEntry.secretKey
                    } else {
                        throw KeyRetrievalFailedException("Failed to retrieve key: Alias found but entry is not a SecretKeyEntry.")
                    }
                } catch (e: Exception) {
                    throw KeyRetrievalFailedException("Failed to retrieve existing key for alias: $KEY_ALIAS", e)
                }
            } else {
                // Key doesn't exist, generate a new one
                try {
                    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
                    val builder = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setKeySize(KEY_SIZE)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

                    // User authentication requirement (API 23+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        builder.setUserAuthenticationRequired(true)
                        // If user authentication is required and the app intends to support
                        // biometric authentication (fingerprint, face etc.) as part of this,
                        // the USE_BIOMETRIC permission should be declared in AndroidManifest.xml:
                        // <uses-permission android:name="android.permission.USE_BIOMETRIC" />
                        // This allows the app to use biometric modalities for authentication.
                        // The Keystore's user authentication prompt will handle presenting
                        // available methods (biometric, PIN, pattern, password).

                        // Set a timeout for how long authentication is valid (API 24+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Requires API Level 24 (Android 7.0 Nougat)
                            builder.setUserAuthenticationValidityDurationSeconds(AUTH_TIMEOUT_SECONDS)
                        }
                    }

                    // Attempt to use StrongBox (API 28+)
                    // Requires API Level 28 (Android 9.0 Pie)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            builder.setIsStrongBoxBacked(true)
                        } catch (e: StrongBoxUnavailableException) {
                            // StrongBox is not available on this device, proceed without it.
                            Log.w(TAG, "StrongBox is unavailable on this device. Proceeding without StrongBox.", e)
                        }
                    }

                    keyGenerator.init(builder.build())
                    return keyGenerator.generateKey()
                } catch (e: Exception) {
                    throw KeyGenerationFailedException("Failed to generate new key for alias: $KEY_ALIAS", e)
                }
            }
        } catch (e: KeyStoreUnavailableException) {
            throw e
        }
        catch (e: Exception) {
            throw KeyStoreUnavailableException("Android Keystore is unavailable or failed to load.", e)
        }
    }
}
