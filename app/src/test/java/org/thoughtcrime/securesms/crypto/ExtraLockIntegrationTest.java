package org.thoughtcrime.securesms.crypto;

import android.os.Build; // Required for Config annotation sdk version

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.crypto.ExtraLockCipher;
import org.thoughtcrime.securesms.crypto.ExtraLockKeyManager;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P) // Using API 28 (P) as a relevant API level (M+ and not latest)
public class ExtraLockIntegrationTest {
    private static final String ACI_A = "partyA_integration_aci";
    private static final String ACI_B = "partyB_integration_aci";
    private static final String KEYSTORE_ALIAS_A = "extralock_" + ACI_A;
    private static final String KEYSTORE_ALIAS_B = "extralock_" + ACI_B;

    @BeforeClass
    public static void setUpClass() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @After
    public void tearDown() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null); // Must load before deleting entries
        if (keyStore.containsAlias(KEYSTORE_ALIAS_A)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS_A);
        }
        if (keyStore.containsAlias(KEYSTORE_ALIAS_B)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS_B);
        }
    }

    @Test
    public void testFullKeyExchangeAndEncryptionDecryptionFlow() throws Exception {
        // 1. Setup Party A
        IdentityKeyPair idKeyPairA = ExtraLockKeyManager.generateKeyPair(ACI_A);
        PrivateKey privateKeyA = ExtraLockKeyManager.getPrivateKey(ACI_A);
        ECPublicKey libsignalPublicKeyA = idKeyPairA.getPublicKey();
        assertNotNull("Party A's private key should not be null", privateKeyA);
        assertNotNull("Party A's public key should not be null", libsignalPublicKeyA);

        // 2. Setup Party B
        IdentityKeyPair idKeyPairB = ExtraLockKeyManager.generateKeyPair(ACI_B);
        PrivateKey privateKeyB = ExtraLockKeyManager.getPrivateKey(ACI_B);
        ECPublicKey libsignalPublicKeyB = idKeyPairB.getPublicKey();
        assertNotNull("Party B's private key should not be null", privateKeyB);
        assertNotNull("Party B's public key should not be null", libsignalPublicKeyB);

        // 3. Simulated Key Exchange & Shared Secret Calculation
        byte[] sharedSecretA = ExtraLockKeyManager.calculateSharedSecret(privateKeyA, libsignalPublicKeyB);
        byte[] sharedSecretB = ExtraLockKeyManager.calculateSharedSecret(privateKeyB, libsignalPublicKeyA);

        assertNotNull("Shared secret A should not be null", sharedSecretA);
        assertNotNull("Shared secret B should not be null", sharedSecretB);
        assertEquals("Shared secret should be 32 bytes for X25519", 32, sharedSecretA.length);
        assertArrayEquals("Shared secrets calculated by A and B must match", sharedSecretA, sharedSecretB);
        assertFalse("Shared secret should not be all zeros", Arrays.equals(new byte[32], sharedSecretA));

        // 4. Encryption (Party A uses the shared secret)
        String commonPassphrase = "integrationTestPassphrase123!";
        ExtraLockCipher cipherA = new ExtraLockCipher(sharedSecretA, commonPassphrase);

        String originalPlaintext = "This is a top secret message for the integration test! It includes various characters like numbers 123 and symbols !@#$.";
        byte[] plaintextBytes = originalPlaintext.getBytes(StandardCharsets.UTF_8);
        byte[] aadBytes = "IntegrationTestAAD_SomeContext_2024".getBytes(StandardCharsets.UTF_8);

        byte[] encryptedData = cipherA.encrypt(plaintextBytes, aadBytes);
        assertNotNull("Encrypted data should not be null", encryptedData);
        assertFalse("Encrypted data should not match original plaintext", Arrays.equals(plaintextBytes, encryptedData));
        // Expected length: nonce (12) + ciphertext (plaintext_len) + tag (16)
        assertEquals("Encrypted data length check", 12 + plaintextBytes.length + 16, encryptedData.length);

        // 5. Decryption (Party B uses the shared secret)
        ExtraLockCipher cipherB = new ExtraLockCipher(sharedSecretB, commonPassphrase);
        byte[] decryptedBytes = cipherB.decrypt(encryptedData, aadBytes);

        assertNotNull("Decrypted data should not be null", decryptedBytes);
        assertArrayEquals("Decrypted data should match original plaintext bytes", plaintextBytes, decryptedBytes);
        assertEquals("Decrypted string should match original plaintext string", originalPlaintext, new String(decryptedBytes, StandardCharsets.UTF_8));

        // 6. Test decryption failure with wrong AAD (Party B)
        byte[] wrongAadBytes = "Wrong_AAD_IntegrationTest".getBytes(StandardCharsets.UTF_8);
        boolean decryptionFailedAsExpected = false;
        try {
            cipherB.decrypt(encryptedData, wrongAadBytes);
        } catch (javax.crypto.AEADBadTagException e) {
            decryptionFailedAsExpected = true;
        }
        assertTrue("Decryption should fail with AEADBadTagException for wrong AAD", decryptionFailedAsExpected);

        // 7. Test decryption failure with wrong passphrase (Party B attempts with different passphrase)
        ExtraLockCipher cipherBWrongPassphrase = new ExtraLockCipher(sharedSecretB, "wrongPassphrase");
        decryptionFailedAsExpected = false;
        try {
            // The key derivation will be different, so the tag won't match.
            cipherBWrongPassphrase.decrypt(encryptedData, aadBytes);
        } catch (javax.crypto.AEADBadTagException e) {
            decryptionFailedAsExpected = true;
        }
        assertTrue("Decryption should fail with AEADBadTagException for wrong passphrase (different key)", decryptionFailedAsExpected);
    }
}
