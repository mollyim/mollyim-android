package org.signal.core.util.crypto

/**
 * Base class for cryptography related exceptions.
 */
open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when key generation fails.
 */
class KeyGenerationFailedException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when the Android Keystore is unavailable.
 */
class KeyStoreUnavailableException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when retrieving a key from the Keystore fails.
 */
class KeyRetrievalFailedException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when decryption fails.
 */
class DecryptionFailedException(message: String, cause: Throwable? = null) : CryptoException(message, cause)
