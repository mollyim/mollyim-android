package org.thoughtcrime.securesms.crypto;

import android.os.Build;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.util.ByteUtil;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P) // API 28, covers "ECDH" path and is M+
public class ExtraLockKeyManagerTest {

    private static final String ACI_A = "testAciA";
    private static final String ACI_B = "testAciB";
    private static final String ACI_CONVERSION = "conversionTestAci";

    private static final String ALIAS_A = "extralock_" + ACI_A;
    private static final String ALIAS_B = "extralock_" + ACI_B;
    private static final String ALIAS_CONVERSION = "extralock_" + ACI_CONVERSION;


    @BeforeClass
    public static void setUpClass() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @After
    public void tearDown() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null); // Must load keystore before deleting
        if (keyStore.containsAlias(ALIAS_A)) {
            keyStore.deleteEntry(ALIAS_A);
        }
        if (keyStore.containsAlias(ALIAS_B)) {
            keyStore.deleteEntry(ALIAS_B);
        }
        if (keyStore.containsAlias(ALIAS_CONVERSION)) {
            keyStore.deleteEntry(ALIAS_CONVERSION);
        }
    }

    @Test
    public void testGenerateKeyPair_shouldReturnValidKeyPair() throws Exception {
        // When
        IdentityKeyPair identityKeyPair = ExtraLockKeyManager.generateKeyPair(ACI_A);

        // Then
        assertNotNull("IdentityKeyPair should not be null", identityKeyPair);
        assertNotNull("Public key should not be null", identityKeyPair.getPublicKey());

        byte[] serializedPublicKey = identityKeyPair.getPublicKey().serialize();
        assertEquals("Serialized public key should be 33 bytes", 33, serializedPublicKey.length);
        assertEquals("Public key type byte should be KEY_TYPE_X25519", Curve.KEY_TYPE_X25519, serializedPublicKey[0]);

        PrivateKey privateKey = ExtraLockKeyManager.getPrivateKey(ACI_A);
        assertNotNull("Retrieved private key should not be null", privateKey);
        assertEquals("AndroidKeyStore", privateKey.getProvider().getName());


        ECPublicKey storedEcPublicKey = ExtraLockKeyManager.getStoredPublicKey(ACI_A);
        assertNotNull("Stored ECPublicKey should not be null", storedEcPublicKey);
        assertArrayEquals("Stored ECPublicKey should match generated public key",
                identityKeyPair.getPublicKey().serialize(), storedEcPublicKey.serialize());
    }

    @Test
    public void testPublicKeyConversion_shouldBeLossless() throws Exception {
        // Given
        IdentityKeyPair idKeyOriginal = ExtraLockKeyManager.generateKeyPair(ACI_CONVERSION);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        PublicKey javaPublicKeyOriginal = keyStore.getCertificate(ALIAS_CONVERSION).getPublicKey();
        assertNotNull("Original Java PublicKey should not be null", javaPublicKeyOriginal);
        byte[] originalEncodedSpki = javaPublicKeyOriginal.getEncoded();

        // When: Android PublicKey -> raw bytes
        byte[] rawKeyBytes = ExtraLockKeyManager.extractX25519PublicKeyBytesFromEncoded(originalEncodedSpki);

        // Then
        assertNotNull("Raw key bytes should not be null", rawKeyBytes);
        assertEquals("Raw key bytes should be 32 bytes long", 32, rawKeyBytes.length);

        // When: raw bytes -> Android PublicKey
        PublicKey javaPublicKeyRestored = ExtraLockKeyManager.convertRawX25519ToJavaPublicKey(rawKeyBytes);

        // Then
        assertNotNull("Restored Java PublicKey should not be null", javaPublicKeyRestored);
        assertArrayEquals("Original SPKI and restored SPKI should match", originalEncodedSpki, javaPublicKeyRestored.getEncoded());

        // When: raw bytes -> libsignal ECPublicKey
        ECPublicKey libsignalPublicKeyFromRaw = Curve.decodePoint(ByteUtil.prepend(rawKeyBytes, Curve.KEY_TYPE_X25519), 0);

        // Then
        assertNotNull("Libsignal ECPublicKey from raw should not be null", libsignalPublicKeyFromRaw);
        assertArrayEquals("Original libsignal ECPublicKey and restored from raw should match",
                idKeyOriginal.getPublicKey().serialize(), libsignalPublicKeyFromRaw.serialize());
    }

    @Test
    public void testCalculateSharedSecret_endToEnd_shouldProduceMatchingSecrets() throws Exception {
        // Given: Party A setup
        IdentityKeyPair keyPairA = ExtraLockKeyManager.generateKeyPair(ACI_A);
        PrivateKey privateKeyA = ExtraLockKeyManager.getPrivateKey(ACI_A);
        ECPublicKey publicKeyA = keyPairA.getPublicKey();

        // Given: Party B setup
        IdentityKeyPair keyPairB = ExtraLockKeyManager.generateKeyPair(ACI_B);
        PrivateKey privateKeyB = ExtraLockKeyManager.getPrivateKey(ACI_B);
        ECPublicKey publicKeyB = keyPairB.getPublicKey();

        // When
        byte[] secretA = ExtraLockKeyManager.calculateSharedSecret(privateKeyA, publicKeyB);
        byte[] secretB = ExtraLockKeyManager.calculateSharedSecret(privateKeyB, publicKeyA);

        // Then
        assertNotNull("Secret A should not be null", secretA);
        assertNotNull("Secret B should not be null", secretB);
        assertEquals("Secret A should be 32 bytes", 32, secretA.length);
        assertEquals("Secret B should be 32 bytes", 32, secretB.length);
        assertArrayEquals("Secret A and Secret B should match", secretA, secretB);
        assertFalse("Shared secret should not be all zeros", Arrays.equals(new byte[32], secretA));
    }

    // Test for API S+ to cover the XDH path in KeyAgreement
    @Test
    @Config(sdk = Build.VERSION_CODES.S)
    public void testCalculateSharedSecret_endToEnd_apiS_shouldProduceMatchingSecrets() throws Exception {
        // Given: Party A setup
        IdentityKeyPair keyPairA = ExtraLockKeyManager.generateKeyPair(ACI_A);
        PrivateKey privateKeyA = ExtraLockKeyManager.getPrivateKey(ACI_A);
        ECPublicKey publicKeyA = keyPairA.getPublicKey();

        // Given: Party B setup
        IdentityKeyPair keyPairB = ExtraLockKeyManager.generateKeyPair(ACI_B);
        PrivateKey privateKeyB = ExtraLockKeyManager.getPrivateKey(ACI_B);
        ECPublicKey publicKeyB = keyPairB.getPublicKey();

        // When
        byte[] secretA = ExtraLockKeyManager.calculateSharedSecret(privateKeyA, publicKeyB);
        byte[] secretB = ExtraLockKeyManager.calculateSharedSecret(privateKeyB, publicKeyA);

        // Then
        assertNotNull("Secret A (API S) should not be null", secretA);
        assertNotNull("Secret B (API S) should not be null", secretB);
        assertEquals("Secret A (API S) should be 32 bytes", 32, secretA.length);
        assertEquals("Secret B (API S) should be 32 bytes", 32, secretB.length);
        assertArrayEquals("Secret A and Secret B (API S) should match", secretA, secretB);
        assertFalse("Shared secret (API S) should not be all zeros", Arrays.equals(new byte[32], secretA));
    }
}
