package im.molly.security

import android.util.Log

/**
 * ML-KEM-1024 Post-Quantum Key Encapsulation Mechanism
 *
 * FIPS 203: Module-Lattice-Based Key-Encapsulation Mechanism Standard
 * https://csrc.nist.gov/pubs/fips/204/final
 *
 * ML-KEM-1024 (formerly CRYSTALS-Kyber-1024) provides:
 * - 256-bit security level (NIST Level 5)
 * - Quantum-resistant key exchange
 * - IND-CCA2 security
 *
 * Key sizes:
 * - Public key: 1568 bytes
 * - Secret key: 3168 bytes
 * - Ciphertext: 1568 bytes
 * - Shared secret: 32 bytes
 *
 * Usage:
 * ```
 * // Key exchange
 * val serverKeyPair = MLKEM1024.generateKeypair()!!
 * val clientResult = MLKEM1024.encapsulate(serverKeyPair.publicKey)!!
 * val serverSecret = MLKEM1024.decapsulate(clientResult.ciphertext, serverKeyPair.secretKey)!!
 *
 * // Both sides now have clientResult.sharedSecret == serverSecret
 * ```
 */
object MLKEM1024 {
    private const val TAG = "MLKEM1024"

    const val PUBLIC_KEY_BYTES = 1568
    const val SECRET_KEY_BYTES = 3168
    const val CIPHERTEXT_BYTES = 1568
    const val SHARED_SECRET_BYTES = 32

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

    data class EncapsulationResult(
        val ciphertext: ByteArray,
        val sharedSecret: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncapsulationResult

            if (!ciphertext.contentEquals(other.ciphertext)) return false
            if (!sharedSecret.contentEquals(other.sharedSecret)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ciphertext.contentHashCode()
            result = 31 * result + sharedSecret.contentHashCode()
            return result
        }
    }

    /**
     * Generate ML-KEM-1024 keypair (FIPS 203)
     *
     * @return KeyPair containing public (1568B) and secret (3168B) keys
     */
    fun generateKeypair(): KeyPair? {
        return try {
            nativeGenerateKeypair()?.also {
                Log.d(TAG, "Generated ML-KEM-1024 keypair (FIPS 203)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-KEM-1024 keypair generation failed", e)
            null
        }
    }

    /**
     * Encapsulate: Generate shared secret and ciphertext using public key
     *
     * @param publicKey Recipient's ML-KEM-1024 public key (1568 bytes)
     * @return EncapsulationResult with ciphertext (1568B) and shared secret (32B)
     */
    fun encapsulate(publicKey: ByteArray): EncapsulationResult? {
        require(publicKey.size == PUBLIC_KEY_BYTES) {
            "Invalid ML-KEM-1024 public key size: ${publicKey.size} (expected $PUBLIC_KEY_BYTES)"
        }

        return try {
            nativeEncapsulate(publicKey)?.also {
                Log.d(TAG, "ML-KEM-1024 encapsulation complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-KEM-1024 encapsulation failed", e)
            null
        }
    }

    /**
     * Decapsulate: Recover shared secret from ciphertext using secret key
     *
     * @param ciphertext Ciphertext from encapsulation (1568 bytes)
     * @param secretKey Your ML-KEM-1024 secret key (3168 bytes)
     * @return Shared secret (32 bytes)
     */
    fun decapsulate(ciphertext: ByteArray, secretKey: ByteArray): ByteArray? {
        require(ciphertext.size == CIPHERTEXT_BYTES) {
            "Invalid ML-KEM-1024 ciphertext size: ${ciphertext.size} (expected $CIPHERTEXT_BYTES)"
        }

        require(secretKey.size == SECRET_KEY_BYTES) {
            "Invalid ML-KEM-1024 secret key size: ${secretKey.size} (expected $SECRET_KEY_BYTES)"
        }

        return try {
            nativeDecapsulate(ciphertext, secretKey)?.also {
                Log.d(TAG, "ML-KEM-1024 decapsulation complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML-KEM-1024 decapsulation failed", e)
            null
        }
    }

    /**
     * Example usage for key exchange
     *
     * @return Pair of (client shared secret, server shared secret) - should be equal
     */
    fun performKeyExchange(): Pair<ByteArray, ByteArray>? {
        // Server side: generate keypair
        val serverKeypair = generateKeypair() ?: return null

        // Client side: encapsulate using server's public key
        val clientResult = encapsulate(serverKeypair.publicKey) ?: return null

        // Server side: decapsulate using secret key
        val serverSharedSecret = decapsulate(
            clientResult.ciphertext,
            serverKeypair.secretKey
        ) ?: return null

        // Both sides now have the same shared secret
        return Pair(clientResult.sharedSecret, serverSharedSecret)
    }

    private external fun nativeGenerateKeypair(): KeyPair?
    private external fun nativeEncapsulate(publicKey: ByteArray): EncapsulationResult?
    private external fun nativeDecapsulate(ciphertext: ByteArray, secretKey: ByteArray): ByteArray?
}
