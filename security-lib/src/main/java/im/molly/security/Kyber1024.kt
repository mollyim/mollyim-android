package im.molly.security

import android.util.Log

/**
 * Kyber-1024 Post-Quantum Key Encapsulation Mechanism
 *
 * NIST PQC standardization finalist
 * Provides quantum-resistant key exchange
 *
 * Key sizes:
 * - Public key: 1568 bytes
 * - Secret key: 3168 bytes
 * - Ciphertext: 1568 bytes
 * - Shared secret: 32 bytes
 */
object Kyber1024 {
    private const val TAG = "Kyber1024"

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
     * Generate Kyber-1024 keypair
     *
     * @return KeyPair containing public and secret keys
     */
    fun generateKeypair(): KeyPair? {
        return try {
            nativeGenerateKeypair()?.also {
                Log.d(TAG, "Generated Kyber-1024 keypair")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keypair generation failed", e)
            null
        }
    }

    /**
     * Encapsulate: Generate shared secret and ciphertext using public key
     *
     * @param publicKey Recipient's public key (1568 bytes)
     * @return EncapsulationResult with ciphertext and shared secret
     */
    fun encapsulate(publicKey: ByteArray): EncapsulationResult? {
        require(publicKey.size == PUBLIC_KEY_BYTES) {
            "Invalid public key size: ${publicKey.size} (expected $PUBLIC_KEY_BYTES)"
        }

        return try {
            nativeEncapsulate(publicKey)?.also {
                Log.d(TAG, "Encapsulated shared secret")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Encapsulation failed", e)
            null
        }
    }

    /**
     * Decapsulate: Recover shared secret from ciphertext using secret key
     *
     * @param ciphertext Ciphertext from encapsulation (1568 bytes)
     * @param secretKey Your secret key (3168 bytes)
     * @return Shared secret (32 bytes)
     */
    fun decapsulate(ciphertext: ByteArray, secretKey: ByteArray): ByteArray? {
        require(ciphertext.size == CIPHERTEXT_BYTES) {
            "Invalid ciphertext size: ${ciphertext.size} (expected $CIPHERTEXT_BYTES)"
        }

        require(secretKey.size == SECRET_KEY_BYTES) {
            "Invalid secret key size: ${secretKey.size} (expected $SECRET_KEY_BYTES)"
        }

        return try {
            nativeDecapsulate(ciphertext, secretKey)?.also {
                Log.d(TAG, "Decapsulated shared secret")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decapsulation failed", e)
            null
        }
    }

    /**
     * Example usage for key exchange
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
