package org.thoughtcrime.securesms.crypto;

import android.os.Build; // Added for @Config

import org.bouncycastle.crypto.digests.SHA256Digest; // Added for HKDF test
import org.bouncycastle.crypto.generators.HKDFBytesGenerator; // Added for HKDF test
import org.bouncycastle.crypto.params.HKDFParameters; // Added for HKDF test
import org.bouncycastle.jce.provider.BouncyCastleProvider; // Added for BouncyCastle setup
import org.junit.Before;
import org.junit.BeforeClass; // Added for BC setup
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
// @Config(manifest = Config.NONE) // Original config
@Config(sdk = Build.VERSION_CODES.P) // Use consistent SDK for tests, P is M+
public class ExtraLockCipherTest {

    // --- Fields for existing encrypt/decrypt tests ---
    private byte[] sharedSecretForCipherTests;
    private String passphraseForCipherTests;
    private ExtraLockCipher extraLockCipherInstanceForCipherTests;

    // --- Fields for HKDF tests ---
    // From RFC 5869 - Test Case 1
    private static final byte[] IKM_RFC5869_CASE1 = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    private static final byte[] SALT_RFC5869_CASE1 = hexToBytes("000102030405060708090a0b0c");
    private static byte[] EXPECTED_OKM_RFC5869_CASE1_WITH_OUR_PARAMS;

    private static final String INFO_STRING_FROM_IMPL = "SignalExtraLockKey"; // Matches ExtraLockCipher.INFO_STRING
    private static final int KEY_LENGTH_BYTES_FROM_IMPL = 32; // Matches ExtraLockCipher.KEY_LENGTH_BYTES

    @BeforeClass
    public static void setUpClass() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // Calculate the expected OKM for RFC5869 Test Case 1 using BouncyCastle HKDF
        // with the specific INFO_STRING and KEY_LENGTH_BYTES used in ExtraLockCipher.
        byte[] info = INFO_STRING_FROM_IMPL.getBytes(StandardCharsets.UTF_8);
        HKDFBytesGenerator hkdfGenerator = new HKDFBytesGenerator(new SHA256Digest());
        hkdfGenerator.init(new HKDFParameters(IKM_RFC5869_CASE1, SALT_RFC5869_CASE1, info));
        EXPECTED_OKM_RFC5869_CASE1_WITH_OUR_PARAMS = new byte[KEY_LENGTH_BYTES_FROM_IMPL];
        hkdfGenerator.generateBytes(EXPECTED_OKM_RFC5869_CASE1_WITH_OUR_PARAMS, 0, KEY_LENGTH_BYTES_FROM_IMPL);
    }

    @Before
    public void setUp() throws Exception {
        // Setup for existing encrypt/decrypt tests
        sharedSecretForCipherTests = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(sharedSecretForCipherTests);
        passphraseForCipherTests = "test_passphrase_for_cipher";
        extraLockCipherInstanceForCipherTests = new ExtraLockCipher(sharedSecretForCipherTests, passphraseForCipherTests);
    }

    // Utility for HKDF tests
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    // --- HKDF Tests ---
    @Test
    public void testDeriveSessionKey_withRfc5869TestVector1() {
        // Arrange: Use a dummy ExtraLockCipher instance to access the package-private deriveSessionKey
        ExtraLockCipher testSubject = new ExtraLockCipher(new byte[32], "dummyPassphraseForHkdfTest");

        // Act
        SecretKey derivedSecretKey = testSubject.deriveSessionKey(IKM_RFC5869_CASE1, SALT_RFC5869_CASE1);
        byte[] actualOkms = derivedSecretKey.getEncoded();

        // Assert
        assertArrayEquals("Derived key material should match RFC5869 test vector 1 (with our specific info/length)",
                          EXPECTED_OKM_RFC5869_CASE1_WITH_OUR_PARAMS, actualOkms);
    }

    @Test
    public void testDeriveSessionKey_deterministic() {
        // Arrange
        byte[] ikm = hexToBytes("aabbccddeeff00112233445566778899"); // Sample IKM
        byte[] salt = hexToBytes("112233445566778899aabbccddeeff00"); // Sample Salt
        ExtraLockCipher testSubject = new ExtraLockCipher(new byte[32], "dummyPassphraseForHkdfTest");

        // Act
        SecretKey key1 = testSubject.deriveSessionKey(ikm, salt);
        SecretKey key2 = testSubject.deriveSessionKey(ikm, salt);

        // Assert
        assertArrayEquals("Derived keys should be identical for the same IKM and salt",
                          key1.getEncoded(), key2.getEncoded());
    }

    @Test
    public void testDeriveSessionKey_sensitiveToSalt() {
        // Arrange
        byte[] ikm = hexToBytes("aabbccddeeff00112233445566778899");
        byte[] salt1 = hexToBytes("112233445566778899aabbccddeeff00");
        byte[] salt2 = hexToBytes("ffeeddccbbaa99887766554433221100"); // Different salt
        ExtraLockCipher testSubject = new ExtraLockCipher(new byte[32], "dummyPassphraseForHkdfTest");

        // Act
        SecretKey key1 = testSubject.deriveSessionKey(ikm, salt1);
        SecretKey key2 = testSubject.deriveSessionKey(ikm, salt2);

        // Assert
        assertFalse("Derived keys should be different for different salts",
                    Arrays.equals(key1.getEncoded(), key2.getEncoded()));
    }

    @Test
    public void testDeriveSessionKey_sensitiveToIkm() {
        // Arrange
        byte[] ikm1 = hexToBytes("aabbccddeeff00112233445566778899");
        byte[] ikm2 = hexToBytes("ffeeddccbbaa99887766554433221100"); // Different IKM
        byte[] salt = hexToBytes("112233445566778899aabbccddeeff00");
        ExtraLockCipher testSubject = new ExtraLockCipher(new byte[32], "dummyPassphraseForHkdfTest");

        // Act
        SecretKey key1 = testSubject.deriveSessionKey(ikm1, salt);
        SecretKey key2 = testSubject.deriveSessionKey(ikm2, salt);

        // Assert
        assertFalse("Derived keys should be different for different IKMs",
                    Arrays.equals(key1.getEncoded(), key2.getEncoded()));
    }

    // --- Existing Encrypt/Decrypt Tests (adapted to use instance from setup) ---
    @Test
    public void encryptDecrypt_success() throws Exception {
        byte[] plaintext = "This is a secret message.".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);
        assertNotNull(nonceAndCiphertext);
        assertNotEquals(0, nonceAndCiphertext.length);
        assertFalse(Arrays.equals(plaintext, Arrays.copyOfRange(nonceAndCiphertext, 12, nonceAndCiphertext.length)));

        byte[] decryptedPlaintext = extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_emptyPlaintext_success() throws Exception {
        byte[] plaintext = "".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "associated_data_empty_plaintext".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);
        byte[] decryptedPlaintext = extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_nullAad_success() throws Exception {
        byte[] plaintext = "Test message with null AAD".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, null);
        byte[] decryptedPlaintext = extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, null);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }

    @Test
    public void encryptDecrypt_emptyAad_success() throws Exception {
        byte[] plaintext = "Test message with empty AAD".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);
        byte[] decryptedPlaintext = extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad);
        assertArrayEquals(plaintext, decryptedPlaintext);
    }


    @Test(expected = AEADBadTagException.class)
    public void decrypt_differentAad_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Test AAD Difference".getBytes(StandardCharsets.UTF_8);
        byte[] aad1 = "first_associated_data".getBytes(StandardCharsets.UTF_8);
        byte[] aad2 = "second_associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad1);
        extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad2); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_nullAadWhenOriginalWasNotEmpty_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Test AAD Difference with Null".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "some_associated_data".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);
        extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, null); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_tamperedCiphertext_throwsAEADBadTagException() throws Exception {
        byte[] plaintext = "Tamper Test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_tamper".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);

        // Tamper the ciphertext part (after nonce)
        if (nonceAndCiphertext.length > 12) { // Ensure there is a ciphertext part
            nonceAndCiphertext[nonceAndCiphertext.length - 1] ^= (byte) 0xFF;
        } else {
            // This case should not happen with ChaCha20Poly1305 if plaintext is not empty leading to tag only
            fail("Ciphertext too short to tamper meaningfully");
        }

        extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad); // Should throw
    }

    @Test(expected = AEADBadTagException.class)
    public void decrypt_tamperedNonce_throwsAEADBadTagExceptionOrSimilar() throws Exception {
        // Behavior with tampered nonce can sometimes lead to other errors before AEADBadTagException
        // depending on the JCE provider, but it must fail integrity.
        byte[] plaintext = "Nonce Tamper Test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_nonce_tamper".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);

        // Tamper the nonce part
        if (nonceAndCiphertext.length >= 12) {
            nonceAndCiphertext[0] ^= (byte) 0xFF;
        } else {
             fail("Ciphertext too short, no nonce to tamper");
        }

        extraLockCipherInstanceForCipherTests.decrypt(nonceAndCiphertext, aad); // Should throw
    }

    @Test
    public void encrypt_producesDifferentNonceAndCiphertext() throws Exception {
        byte[] plaintext = "Unique encryption test".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad_unique_test".getBytes(StandardCharsets.UTF_8);

        byte[] nonceAndCiphertext1 = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);
        byte[] nonceAndCiphertext2 = extraLockCipherInstanceForCipherTests.encrypt(plaintext, aad);

        // Check that full outputs (nonce + ciphertext) are different due to different nonces
        assertFalse(Arrays.equals(nonceAndCiphertext1, nonceAndCiphertext2));

        // Check that nonces are different
        byte[] nonce1 = Arrays.copyOfRange(nonceAndCiphertext1, 0, 12);
        byte[] nonce2 = Arrays.copyOfRange(nonceAndCiphertext2, 0, 12);
        assertFalse(Arrays.equals(nonce1, nonce2));
    }
}
