package im.molly.security

import android.util.Log

/**
 * ML-DSA-87 Post-Quantum Digital Signature Algorithm
 *
 * FIPS 204: Module-Lattice-Based Digital Signature Standard
 * https://csrc.nist.gov/pubs/fips/204/final
 *
 * ML-DSA-87 (formerly CRYSTALS-Dilithium5) provides:
 * - 256-bit security level (NIST Level 5)
 * - Quantum-resistant digital signatures
 * - Strong EUF-CMA security
 * - Deterministic signatures (no RNG during signing)
 *
 * Key and signature sizes:
 * - Public key: 2592 bytes
 * - Secret key: 4864 bytes
 * - Signature: 4627 bytes
 *
 * Usage:
 * ```
 * // Sign a message
 * val keyPair = MLDSA87.generateKeypair()!!
 * val message = "Important message".toByteArray()
 * val signature = MLDSA87.sign(message, keyPair.secretKey)!!
 *
 * // Verify signature
 * val valid = MLDSA87.verify(message, signature, keyPair.publicKey)
 * ```
 */
object MLDSA87 {
    private const val TAG = "MLDSA87"

    const val PUBLIC_KEY_BYTES = 2592
    const val SECRET_KEY_BYTES = 4864
    const val SIGNATURE_BYTES = 4627

    init {
        try {
            System.loadLibrary("molly_security")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    data class KeyPair(
        val publicKey: ByteArray,
        val secretKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyPair

            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!secretKey.contentEquals(other.secretKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + secretKey.contentHashCode()
            return result
        }
    }

    /**
     * Generate ML-DSA-87 signing keypair (FIPS 204)
     *
     * @return KeyPair containing public (2592B) and secret (4864B) keys
     */
    fun generateKeypair(): KeyPair? {
        return try {
            nativeGenerateKeypair()?.also {
                Log.d(TAG, "Generated ML-DSA-87 keypair (FIPS 204)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-DSA-87 keypair generation failed", e)
            null
        }
    }

    /**
     * Sign a message using ML-DSA-87
     *
     * @param message Message to sign (any length)
     * @param secretKey Your ML-DSA-87 secret key (4864 bytes)
     * @return Signature (4627 bytes), or null on error
     */
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray? {
        require(secretKey.size == SECRET_KEY_BYTES) {
            "Invalid ML-DSA-87 secret key size: ${secretKey.size} (expected $SECRET_KEY_BYTES)"
        }

        return try {
            nativeSign(message, secretKey)?.also {
                Log.d(TAG, "ML-DSA-87 signature created (${message.size} byte message)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-DSA-87 signing failed", e)
            null
        }
    }

    /**
     * Sign a message (String convenience method)
     *
     * @param message Message string to sign
     * @param secretKey Your ML-DSA-87 secret key (4864 bytes)
     * @return Signature (4627 bytes), or null on error
     */
    fun sign(message: String, secretKey: ByteArray): ByteArray? {
        return sign(message.toByteArray(Charsets.UTF_8), secretKey)
    }

    /**
     * Verify a signature using ML-DSA-87
     *
     * @param message Original message
     * @param signature Signature to verify (4627 bytes)
     * @param publicKey Signer's ML-DSA-87 public key (2592 bytes)
     * @return true if signature is valid, false otherwise
     */
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        require(signature.size == SIGNATURE_BYTES) {
            "Invalid ML-DSA-87 signature size: ${signature.size} (expected $SIGNATURE_BYTES)"
        }

        require(publicKey.size == PUBLIC_KEY_BYTES) {
            "Invalid ML-DSA-87 public key size: ${publicKey.size} (expected $PUBLIC_KEY_BYTES)"
        }

        return try {
            nativeVerify(message, signature, publicKey).also { valid ->
                Log.d(TAG, "ML-DSA-87 signature verification: ${if (valid) "VALID" else "INVALID"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-DSA-87 verification failed", e)
            false
        }
    }

    /**
     * Verify a signature (String convenience method)
     *
     * @param message Original message string
     * @param signature Signature to verify (4627 bytes)
     * @param publicKey Signer's ML-DSA-87 public key (2592 bytes)
     * @return true if signature is valid, false otherwise
     */
    fun verify(message: String, signature: ByteArray, publicKey: ByteArray): Boolean {
        return verify(message.toByteArray(Charsets.UTF_8), signature, publicKey)
    }

    /**
     * Example usage for signing and verifying
     *
     * @param message Message to sign and verify
     * @return true if signature validates correctly
     */
    fun performSignAndVerify(message: ByteArray): Boolean {
        // Generate keypair
        val keypair = generateKeypair() ?: return false

        // Sign message
        val signature = sign(message, keypair.secretKey) ?: return false

        // Verify signature
        return verify(message, signature, keypair.publicKey)
    }

    private external fun nativeGenerateKeypair(): KeyPair?
    private external fun nativeSign(message: ByteArray, secretKey: ByteArray): ByteArray?
    private external fun nativeVerify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}
