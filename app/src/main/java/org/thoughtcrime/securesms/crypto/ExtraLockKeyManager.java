package org.thoughtcrime.securesms.crypto;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.util.ByteUtil;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey as JavaECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.Provider;
import java.security.Security;

import javax.crypto.KeyAgreement;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class ExtraLockKeyManager {

    private static final String ANDROID_KEY_STORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS_PREFIX = "extralock_";

    @RequiresApi(Build.VERSION_CODES.M)
    public static IdentityKeyPair generateKeyPair(@NonNull String localAci) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, InvalidAlgorithmParameterException, NoSuchProviderException, java.security.spec.InvalidKeySpecException {
        String alias = KEY_ALIAS_PREFIX + localAci;

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE_PROVIDER);

        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_AGREE_KEY);

        // For API 23 (M) and above, "X25519" should be a recognized curve name for KeyPairGenerator
        // when the purpose is KeyAgreement and the underlying provider supports it.
        // KeyAgreement itself supports "XDH" or "Curve25519" from API 23.
        // KeyProperties.KEY_ALGORITHM_XDH is defined from API 31, but the curve itself is usable earlier.
        specBuilder.setAlgorithmParameterSpec(new ECGenParameterSpec("X25519"));

        keyPairGenerator.initialize(specBuilder.build());
        KeyPair javaKeyPair = keyPairGenerator.generateKeyPair();
        PublicKey javaPublicKey = javaKeyPair.getPublic();
        byte[] publicKeyBytes;

        if (javaPublicKey instanceof JavaECPublicKey) {
            // We expect an X25519 key. The actual parsing of the SPKI format
            // to get raw bytes is handled by extractX25519PublicKeyBytesFromEncoded.
            publicKeyBytes = extractX25519PublicKeyBytesFromEncoded(javaPublicKey.getEncoded());
        } else {
            throw new InvalidKeyException("Unsupported public key type, expected JavaECPublicKey: " + javaPublicKey.toString());
        }

        ECPublicKey ecPublicKey = Curve.decodePoint(ByteUtil.prepend(publicKeyBytes, Curve.KEY_TYPE_X25519), 0);

        // The private key remains in the Keystore, referenced by 'alias'.
        // ECPrivateKey for IdentityKeyPair is problematic as material isn't directly available.
        // Create a placeholder ECPrivateKey. Operations requiring the private key will use the Keystore PrivateKey object directly.
        ECPrivateKey ecPrivateKey = Curve.decodePrivatePoint(new byte[32]); // Placeholder, not the actual private key material

        return new IdentityKeyPair(ecPublicKey, ecPrivateKey);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static PrivateKey getPrivateKey(@NonNull String localAci) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        String alias = KEY_ALIAS_PREFIX + localAci;
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER);
        keyStore.load(null);

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
        if (privateKey == null) {
            throw new UnrecoverableKeyException("Key not found for ACI: " + localAci);
        }
        return privateKey;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static ECPublicKey getStoredPublicKey(@NonNull String localAci) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, java.security.spec.InvalidKeySpecException, InvalidKeyException {
        String alias = KEY_ALIAS_PREFIX + localAci;
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER);
        keyStore.load(null);
        PublicKey javaPublicKey = keyStore.getCertificate(alias).getPublicKey();
        if (javaPublicKey == null) {
            throw new UnrecoverableKeyException("Public Key not found for ACI: " + localAci);
        }

        byte[] rawPublicKeyBytes;
        if (javaPublicKey instanceof JavaECPublicKey) {
            rawPublicKeyBytes = extractX25519PublicKeyBytesFromEncoded(javaPublicKey.getEncoded());
        } else {
             throw new InvalidKeyException("Unsupported public key type, expected JavaECPublicKey: " + javaPublicKey.toString());
        }
        return Curve.decodePoint(ByteUtil.prepend(rawPublicKeyBytes, Curve.KEY_TYPE_X25519), 0);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static byte[] calculateSharedSecret(@NonNull PrivateKey localPrivateKey, @NonNull ECPublicKey peerPublicKey) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException, java.security.spec.InvalidKeySpecException, IOException {
        if (!ANDROID_KEY_STORE_PROVIDER.equals(localPrivateKey.getProvider().getName())) {
            throw new InvalidKeyException("Local private key for ECDH must be from the AndroidKeyStore provider. Found: " + localPrivateKey.getProvider().getName());
        }

        // Note: localPrivateKey is from AndroidKeyStore.
        // peerPublicKey is a libsignal ECPublicKey. It needs to be converted to java.security.PublicKey for KeyAgreement.

        KeyAgreement keyAgreement;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyAgreement = KeyAgreement.getInstance(KeyProperties.KEY_ALGORITHM_XDH, ANDROID_KEY_STORE_PROVIDER);
        } else {
            // For API levels M-R (23-30), "ECDH" is used. The actual curve (X25519)
            // is determined by the key material generated in AndroidKeyStore.
            keyAgreement = KeyAgreement.getInstance("ECDH", ANDROID_KEY_STORE_PROVIDER);
        }
        keyAgreement.init(localPrivateKey);

        byte[] peerPublicKeySerialized = peerPublicKey.serialize();
        byte[] x25519PeerPublicKeyBytes = new byte[32];

        if (peerPublicKeySerialized.length == 33 && peerPublicKeySerialized[0] == Curve.KEY_TYPE_X25519) {
            System.arraycopy(peerPublicKeySerialized, 1, x25519PeerPublicKeyBytes, 0, 32);
        } else if (peerPublicKeySerialized.length == 32) { // Assuming raw key if length is 32
             System.arraycopy(peerPublicKeySerialized, 0, x25519PeerPublicKeyBytes, 0, 32);
        } else {
            throw new InvalidKeyException("Invalid peer public key format. Expected 32 bytes raw or 33 bytes with type prefix.");
        }

        PublicKey javaPeerPublicKey = convertRawX25519ToJavaPublicKey(x25519PeerPublicKeyBytes);

        keyAgreement.doPhase(javaPeerPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * Extracts raw X25519 public key bytes from an X.509 SubjectPublicKeyInfo encoded byte array.
     *
     * @param encodedKey The X.509 encoded public key.
     * @return The 32 raw public key bytes.
     * @throws InvalidKeyException If the key is not a valid X25519 SPKI.
     */
    private static byte[] extractX25519PublicKeyBytesFromEncoded(byte[] encodedKey) throws InvalidKeyException {
        Provider bcProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (bcProvider == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(encodedKey);
            AlgorithmIdentifier algId = spki.getAlgorithm();

            // OID for X25519 public key is 1.3.101.112 (EdECObjectIdentifiers.id_X25519)
            if (!EdECObjectIdentifiers.id_X25519.equals(algId.getAlgorithm())) {
                throw new InvalidKeyException("PublicKey OID is not id-X25519. Found OID: " + algId.getAlgorithm().getId());
            }

            // For X25519, the public key data is directly the 32 raw bytes.
            // spki.getPublicKeyData() returns a DERBitString. getOctets() provides the raw byte content of the bit string.
            byte[] rawKey = spki.getPublicKeyData().getOctets();

            if (rawKey.length != 32) {
                throw new InvalidKeyException("Extracted X25519 public key is not 32 bytes. Length: " + rawKey.length);
            }
            return rawKey;

        } catch (Exception e) {
            throw new InvalidKeyException("Failed to parse X25519 SubjectPublicKeyInfo from encoded key. " + e.getMessage(), e);
        }
    }

    /**
     * Converts raw X25519 public key bytes into a {@link PublicKey} object
     * by wrapping them in an X.509 SubjectPublicKeyInfo structure.
     *
     * @param rawX25519PublicKeyBytes The 32 raw X25519 public key bytes.
     * @return A {@link PublicKey} instance.
     * @throws NoSuchAlgorithmException If the required KeyFactory algorithms are unavailable.
     * @throws java.security.spec.InvalidKeySpecException If the key spec is invalid.
     * @throws IOException If there's an error encoding the SubjectPublicKeyInfo.
     */
    private static PublicKey convertRawX25519ToJavaPublicKey(byte[] rawX25519PublicKeyBytes)
            throws NoSuchAlgorithmException, java.security.spec.InvalidKeySpecException, IOException {

        if (rawX25519PublicKeyBytes == null || rawX25519PublicKeyBytes.length != 32) {
            throw new java.security.spec.InvalidKeySpecException("Raw X25519 public key must be 32 bytes.");
        }

        Provider bcProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (bcProvider == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        try {
            AlgorithmIdentifier algId = new AlgorithmIdentifier(EdECObjectIdentifiers.id_X25519);
            DERBitString publicKeyData = new DERBitString(rawX25519PublicKeyBytes);
            SubjectPublicKeyInfo spki = new SubjectPublicKeyInfo(algId, publicKeyData);
            byte[] x509EncodedKeyBytes = spki.getEncoded();

            KeyFactory keyFactory;
            try {
                keyFactory = KeyFactory.getInstance("XDH");
            } catch (NoSuchAlgorithmException e) {
                try {
                   keyFactory = KeyFactory.getInstance("X25519", BouncyCastleProvider.PROVIDER_NAME);
                } catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
                   throw new NoSuchAlgorithmException("Neither XDH nor X25519 KeyFactory available to convert X25519 key.", ex);
                }
            }

            return keyFactory.generatePublic(new X509EncodedKeySpec(x509EncodedKeyBytes));

        } catch (IOException e) {
            throw new IOException("Failed to DER-encode SubjectPublicKeyInfo for X25519 key: " + e.getMessage(), e);
        } catch (Exception e) { // Catch any other unexpected BouncyCastle or KeyFactory errors
            throw new java.security.spec.InvalidKeySpecException("Failed to convert raw X25519 key to PublicKey: " + e.getMessage(), e);
        }
    }
}
