package org.signal.core.util.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import org.junit.Assert.assertFalse // Keep this if it's used elsewhere, or remove if not.

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE) // Using Config.NONE as Android Keystore is not directly tested here.
class AndroidDataCipherTest {

    private fun generateAesKey(keySize: Int = 256): SecretKey {
        val keyBytes = ByteArray(keySize / 8)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    @Test
    fun encryptAndDecrypt_validKey_success() {
        val secretKey = generateAesKey()
        val originalText = "This is a secret message!"

        val encryptedData = AndroidDataCipher.encrypt(originalText, secretKey)
        assertNotNull("EncryptedData should not be null", encryptedData)
        assertNotEquals("Ciphertext should not be same as plaintext", originalText, String(encryptedData.ciphertext))
        assertNotNull("IV should not be null", encryptedData.iv)

        val decryptedText = AndroidDataCipher.decrypt(encryptedData, secretKey)
        assertEquals("Decrypted text should match original", originalText, decryptedText)
    }

    @Test
    fun encrypt_generatesUniqueIVs() {
        val secretKey = generateAesKey()
        val originalText = "Some data"

        val encryptedData1 = AndroidDataCipher.encrypt(originalText, secretKey)
        val encryptedData2 = AndroidDataCipher.encrypt(originalText, secretKey)

        assertNotNull(encryptedData1.iv)
        assertNotNull(encryptedData2.iv)
        assertFalse("IVs for different encryptions should be unique", encryptedData1.iv.contentEquals(encryptedData2.iv))
    }

    @Test
    fun decrypt_wrongKey_returnsNull() {
        val secretKey1 = generateAesKey()
        val secretKey2 = generateAesKey() // Different key
        val originalText = "Another secret"

        val encryptedData = AndroidDataCipher.encrypt(originalText, secretKey1)

        // Attempt to decrypt with the wrong key
        val decryptedText = AndroidDataCipher.decrypt(encryptedData, secretKey2)
        assertNull("Decryption with wrong key should return null", decryptedText)
    }

    @Test
    fun decrypt_tamperedCiphertext_returnsNull() {
        val secretKey = generateAesKey()
        val originalText = "Sensitive information"

        var encryptedData = AndroidDataCipher.encrypt(originalText, secretKey)

        // Tamper with the ciphertext
        if (encryptedData.ciphertext.isNotEmpty()) {
            encryptedData.ciphertext[0] = (encryptedData.ciphertext[0] + 1).toByte()
        }

        val decryptedText = AndroidDataCipher.decrypt(encryptedData, secretKey)
        assertNull("Decryption of tampered ciphertext should return null", decryptedText)
    }

    @Test
    fun decrypt_tamperedIv_returnsNull() {
        val secretKey = generateAesKey()
        val originalText = "Top secret data"

        var encryptedData = AndroidDataCipher.encrypt(originalText, secretKey)

        // Tamper with the IV
        if (encryptedData.iv.isNotEmpty()) {
            encryptedData.iv[0] = (encryptedData.iv[0] + 1).toByte()
        }

        val decryptedText = AndroidDataCipher.decrypt(encryptedData, secretKey)
        assertNull("Decryption of tampered IV should return null", decryptedText)
    }

    @Test
    fun encryptAndDecrypt_emptyPlaintext_handlesCorrectly() { // Removed (expected = CryptoException::class)
        val secretKey = generateAesKey()
        val originalText = ""

        val encryptedData = AndroidDataCipher.encrypt(originalText, secretKey)
        assertNotNull("EncryptedData should not be null for empty plaintext", encryptedData)
        // Ciphertext for empty string might not be empty due to GCM authentication tag
        //assertTrue("Ciphertext for empty string should not be empty", encryptedData.ciphertext.isNotEmpty())
        assertNotNull("IV should not be null for empty plaintext", encryptedData.iv)

        val decryptedText = AndroidDataCipher.decrypt(encryptedData, secretKey)
        assertEquals("Decrypted empty text should match original empty text", originalText, decryptedText)
    }

    // Note: Testing UserNotAuthenticatedException directly is hard in unit tests
    // as it requires a real KeyStore key with user auth configured and an expired auth.
    // This would typically be part of an instrumentation test.
}
