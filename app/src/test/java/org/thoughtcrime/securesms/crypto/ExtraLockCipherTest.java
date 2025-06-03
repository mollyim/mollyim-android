package org.thoughtcrime.securesms.crypto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ExtraLockCipherTest {

    private byte[] sharedSecret;
    private String passphrase;
    private ExtraLockCipher extraLockCipher;

    @Before
    public void setUp() throws Exception {
        sharedSecret = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(sharedSecret); // Example shared secret
        passphrase = "test_passphrase";
        extraLockCipher = new ExtraLockCipher(sharedSecret, passphrase);
    }

    @Test
    public void encryptDecrypt_success() throws Exception {
        byte[] plaintext = "This is a secret message.".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);
        assertNotNull(nonceAndCiphertext);
        assertNotEquals(0, nonceAndCiphertext.length);
        assertFalse(Arrays.equals(plaintext, Arrays.copyOfRange(nonceAndCiphertext, 12, nonceAndCiphertext.length)));

        byte[] decryptedPlaintext = extraLockCipher.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_emptyPlaintext_success() throws Exception {
        byte[] plaintext = "".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "associated_data_empty_plaintext".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);
        byte[] decryptedPlaintext = extraLockCipher.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_nullAad_success() throws Exception {
        byte[] plaintext = "Test message with null AAD".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, null);
        byte[] decryptedPlaintext = extraLockCipher.decrypt(nonceAndCiphertext, null);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_emptyAad_success() throws Exception {
        byte[] plaintext = "Test message with empty AAD".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);
        byte[] decryptedPlaintext = extraLockCipher.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }


    @Test(expected = AEADBadTagException.class)
    public void decrypt_differentAad_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Test AAD Difference".getBytes(StandardCharsets.UTF_8);
        byte[] aad1 = "first_associated_data".getBytes(StandardCharsets.UTF_8);
        byte[] aad2 = "second_associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad1);
        extraLockCipher.decrypt(nonceAndCiphertext, aad2); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_nullAadWhenOriginalWasNotEmpty_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Test AAD Difference with Null".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "some_associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);
        extraLockCipher.decrypt(nonceAndCiphertext, null); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_tamperedCiphertext_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Tamper Test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_tamper".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);

        // Tamper the ciphertext part (after nonce)
        if (nonceAndCiphertext.length > 12) { // Ensure there is a ciphertext part
            nonceAndCiphertext[nonceAndCiphertext.length - 1] ^= (byte) 0xFF;
        } else {
            // This case should not happen with ChaCha20Poly1305 if plaintext is not empty leading to tag only
            fail("Ciphertext too short to tamper meaningfully");
        }

        extraLockCipher.decrypt(nonceAndCiphertext, aad); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_tamperedNonce_throwsAEADBadTagExceptionOrSimilar() throws Exception {
        // Behavior with tampered nonce can sometimes lead to other errors before AEADBadTagException
        // depending on the JCE provider, but it must fail integrity.
        byte[] plaintext = "Nonce Tamper Test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_nonce_tamper".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipher.encrypt(plaintext, aad);

        // Tamper the nonce part
        if (nonceAndCiphertext.length >= 12) {
            nonceAndCiphertext[0] ^= (byte) 0xFF;
        } else {
             fail("Ciphertext too short, no nonce to tamper");
        }

        extraLockCipher.decrypt(nonceAndCiphertext, aad); // Should throw
    }

    @Test
    public void encrypt_producesDifferentNonceAndCiphertext() throws Exception {
        byte[] plaintext = "Unique encryption test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_unique_test".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext1 = extraLockCipher.encrypt(plaintext, aad);
        byte[] nonceAndCiphertext2 = extraLockCipher.encrypt(plaintext, aad);

        // Check that full outputs (nonce + ciphertext) are different due to different nonces
        assertFalse(Arrays.equals(nonceAndCiphertext1, nonceAndCiphertext2));

        // Check that nonces are different
        byte[] nonce1 = Arrays.copyOfRange(nonceAndCiphertext1, 0, 12);
        byte[] nonce2 = Arrays.copyOfRange(nonceAndCiphertext2, 0, 12);
        assertFalse(Arrays.equals(nonce1, nonce2));
    }
}
