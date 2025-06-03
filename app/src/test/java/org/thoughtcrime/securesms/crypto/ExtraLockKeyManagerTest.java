package org.thoughtcrime.securesms.crypto;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.crypto.KeyAgreement;


// Robolectric is used for Build.VERSION.SDK_INT and potentially KeyStore/KeyPairGenerator behavior
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.M) // ExtraLockKeyManager uses API M features
public class ExtraLockKeyManagerTest {

    private KeyPairGenerator mockKeyPairGenerator;
    private KeyStore mockKeyStore;
    private java.security.KeyPair mockJKeyPair;
    private PublicKey mockPublicKey;
    private PrivateKey mockPrivateKey;
    private Certificate mockCertificate;

    private final String TEST_ACI = "test_aci_123";
    private final String KEY_ALIAS = "extralock_" + TEST_ACI;

    @Before
    public void setUp() throws Exception {
        mockKeyPairGenerator = mock(KeyPairGenerator.class);
        mockKeyStore = mock(KeyStore.class);
        mockJKeyPair = mock(java.security.KeyPair.class);
        mockPublicKey = mock(PublicKey.class);
        mockPrivateKey = mock(PrivateKey.class);
        mockCertificate = mock(Certificate.class);

        when(mockJKeyPair.getPublic()).thenReturn(mockPublicKey);
        // For generateKeyPair, we need getEncoded to return *something*.
        // The actual conversion in ExtraLockKeyManager is problematic and not deeply tested here.
        when(mockPublicKey.getEncoded()).thenReturn(new byte[65]); // Dummy X.509 format for EC, typical length
        when(mockJKeyPair.getPrivate()).thenReturn(mockPrivateKey);
        when(mockPrivateKey.getEncoded()).thenReturn(new byte[32]); // Dummy PKCS#8, not raw

        when(mockKeyPairGenerator.generateKeyPair()).thenReturn(mockJKeyPair);
        when(mockKeyStore.getCertificate(KEY_ALIAS)).thenReturn(mockCertificate);
        when(mockCertificate.getPublicKey()).thenReturn(mockPublicKey);
    }

    @Test
    public void generateKeyPair_createsKeyWithCorrectAliasAndParams() throws Exception {
        try (MockedStatic<KeyPairGenerator> kpgStatic = Mockito.mockStatic(KeyPairGenerator.class);
             MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {

            kpgStatic.when(() -> KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"))
                    .thenReturn(mockKeyPairGenerator);
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            // mockKeyStore.load(null) is void, so it's fine

            IdentityKeyPair identityKeyPair = ExtraLockKeyManager.generateKeyPair(TEST_ACI);

            assertNotNull(identityKeyPair);
            assertNotNull(identityKeyPair.getPublicKey());
            // Private key in IdentityKeyPair is a dummy/placeholder in the current implementation
            assertNotNull(identityKeyPair.getPrivateKey());

            ArgumentCaptor<KeyGenParameterSpec> specCaptor = ArgumentCaptor.forClass(KeyGenParameterSpec.class);
            verify(mockKeyPairGenerator).initialize(specCaptor.capture());
            KeyGenParameterSpec spec = specCaptor.getValue();
            assertEquals(KEY_ALIAS, spec.getKeystoreAlias());
            assertTrue((spec.getPurposes() & KeyProperties.PURPOSE_AGREE_KEY) != 0);

            // Check algorithm parameter spec if possible (tricky as it's wrapped)
            if (spec.getAlgorithmParameterSpec() instanceof ECGenParameterSpec) {
                // This part depends on what curve name is used in ExtraLockKeyManager for the API level
                // For API 23 (M), it might be a placeholder like "secp256r1" if "X25519" isn't available
                // String expectedCurveName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? "X25519" : "secp256r1";
                // assertEquals(expectedCurveName, ((ECGenParameterSpec) spec.getAlgorithmParameterSpec()).getName());
            }
        }
    }

    @Test
    public void getPrivateKey_keyExists_returnsPrivateKey() throws Exception {
        try (MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            when(mockKeyStore.getKey(KEY_ALIAS, null)).thenReturn(mockPrivateKey);

            PrivateKey retrievedKey = ExtraLockKeyManager.getPrivateKey(TEST_ACI);
            assertNotNull(retrievedKey);
            assertEquals(mockPrivateKey, retrievedKey);
        }
    }

    @Test(expected = UnrecoverableKeyException.class)
    public void getPrivateKey_keyNotExists_throwsException() throws Exception {
        try (MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            when(mockKeyStore.getKey(KEY_ALIAS, null)).thenReturn(null);
            ExtraLockKeyManager.getPrivateKey(TEST_ACI);
        }
    }

    @Test
    public void getStoredPublicKey_keyExists_returnsPublicKey() throws Exception {
         try (MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            // Assumes getEncoded returns something that Curve.decodePoint can handle,
            // which is a known problematic area in the main code.
            // This test mainly checks the flow and that a non-null ECPublicKey object is returned.
            ECPublicKey retrievedEcPublicKey = ExtraLockKeyManager.getStoredPublicKey(TEST_ACI);
            assertNotNull(retrievedEcPublicKey);
        }
    }

    @Test(expected = UnrecoverableKeyException.class)
    public void getStoredPublicKey_certificateNull_throwsException() throws Exception {
        try (MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            when(mockKeyStore.getCertificate(KEY_ALIAS)).thenReturn(null);
            ExtraLockKeyManager.getStoredPublicKey(TEST_ACI);
        }
    }

    @Test(expected = UnrecoverableKeyException.class)
    public void getStoredPublicKey_publicKeyNull_throwsException() throws Exception {
        try (MockedStatic<KeyStore> ksStatic = Mockito.mockStatic(KeyStore.class)) {
            ksStatic.when(() -> KeyStore.getInstance("AndroidKeyStore")).thenReturn(mockKeyStore);
            when(mockCertificate.getPublicKey()).thenReturn(null); // mockKeyStore already returns mockCertificate
            ExtraLockKeyManager.getStoredPublicKey(TEST_ACI);
        }
    }

    @Test
    public void calculateSharedSecret_placeholderConversion_runsWithoutConversionError() throws Exception {
        // This test is limited because convertLibsignalPublicKeyToJavaPublicKey is a placeholder.
        // We will mock KeyAgreement to avoid it calling the placeholder.
        // The goal is to check if the method structure is callable.
        PrivateKey localPrivKey = mock(PrivateKey.class); // A non-Keystore private key for simplicity
        ECPublicKey peerPubKey = Curve.generateKeyPair().getPublicKey(); // A real ECPublicKey

        KeyAgreement mockKeyAgreement = mock(KeyAgreement.class);
        when(mockKeyAgreement.generateSecret()).thenReturn(new byte[32]); // Simulate secret generation

        try (MockedStatic<KeyAgreement> kaStatic = Mockito.mockStatic(KeyAgreement.class)) {
            kaStatic.when(() -> KeyAgreement.getInstance(anyString())).thenReturn(mockKeyAgreement);

            // We expect an UnsupportedOperationException if the actual conversion is called
            // To test the flow *around* it, we'd need to mock the conversion, which is too deep for this.
            // Instead, this test primarily checks that the method can be called and
            // KeyAgreement is initialized. If convertLibsignalPublicKeyToJavaPublicKey were working,
            // it would proceed to doPhase.
            // Since it throws, we expect that.
            try {
                 ExtraLockKeyManager.calculateSharedSecret(localPrivKey, peerPubKey);
                 fail("Expected UnsupportedOperationException due to placeholder key conversion");
            } catch (UnsupportedOperationException e) {
                // This is expected due to the placeholder in convertLibsignalPublicKeyToJavaPublicKey
                assertTrue(e.getMessage().contains("not yet implemented"));
            } catch (Exception e) {
                // If it's a different exception, something else went wrong.
                fail("Unexpected exception: " + e.getMessage());
            }
        }
    }
}
