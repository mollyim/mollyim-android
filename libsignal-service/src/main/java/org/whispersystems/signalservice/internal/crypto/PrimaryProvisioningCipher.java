/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.crypto;

import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.signal.registration.proto.RegistrationProvisionEnvelope;
import org.signal.registration.proto.RegistrationProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisionEnvelope;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okio.ByteString;

public class PrimaryProvisioningCipher {

  public static final String PROVISIONING_MESSAGE = "TextSecure Provisioning Message";

  private final ECPublicKey theirPublicKey;

  public PrimaryProvisioningCipher(ECPublicKey theirPublicKey) {
    this.theirPublicKey = theirPublicKey;
  }

  public byte[] encrypt(ProvisionMessage message) throws InvalidKeyException {
    ECKeyPair ourKeyPair    = ECKeyPair.generate();
    byte[]    sharedSecret  = ourKeyPair.getPrivateKey().calculateAgreement(theirPublicKey);
    byte[]    derivedSecret = HKDF.deriveSecrets(sharedSecret, PROVISIONING_MESSAGE.getBytes(), 64);
    byte[][]  parts         = Util.split(derivedSecret, 32, 32);

    byte[] version    = {0x01};
    byte[] ciphertext = getCiphertext(parts[0], message.encode());
    byte[] mac        = getMac(parts[1], Util.join(version, ciphertext));
    byte[] body       = Util.join(version, ciphertext, mac);

    return new ProvisionEnvelope.Builder()
                                .publicKey(ByteString.of(ourKeyPair.getPublicKey().serialize()))
                                .body(ByteString.of(body))
                                .build()
                                .encode();
  }

  public byte[] encrypt(RegistrationProvisionMessage message) throws InvalidKeyException {
    ECKeyPair ourKeyPair    = ECKeyPair.generate();
    byte[]    sharedSecret  = ourKeyPair.getPrivateKey().calculateAgreement(theirPublicKey);
    byte[]    derivedSecret = HKDF.deriveSecrets(sharedSecret, PROVISIONING_MESSAGE.getBytes(), 64);
    byte[][]  parts         = Util.split(derivedSecret, 32, 32);

    byte[] version    = { 0x00 };
    byte[] ciphertext = getCiphertext(parts[0], message.encode());
    byte[] mac        = getMac(parts[1], Util.join(version, ciphertext));
    byte[] body       = Util.join(version, ciphertext, mac);

    return new RegistrationProvisionEnvelope.Builder()
                                            .publicKey(ByteString.of(ourKeyPair.getPublicKey().serialize()))
                                            .body(ByteString.of(body))
                                            .build()
                                            .encode();
  }

  private byte[] getCiphertext(byte[] key, byte[] message) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

      return Util.join(cipher.getIV(), cipher.doFinal(message));
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getMac(byte[] key, byte[] message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public ProvisionMessage decrypt(IdentityKeyPair tempIdentity, byte[] bytes) throws InvalidKeyException, IOException {
    ProvisionEnvelope envelope      = ProvisionEnvelope.ADAPTER.decode(bytes);
    ECPublicKey       publicKey     = new ECPublicKey(envelope.publicKey.toByteArray());
    byte[]            sharedSecret  = tempIdentity.getPrivateKey().calculateAgreement(publicKey);
    byte[]            derivedSecret = HKDF.deriveSecrets(sharedSecret, PROVISIONING_MESSAGE.getBytes(), 64);
    byte[][]          parts         = Util.split(derivedSecret, 32, 32);
    ByteString        joined        = envelope.body;
    if (joined.getByte(0) != 0x01) {
      throw new RuntimeException("Bad version number on provision message!");
    }
    byte[] iv              = joined.substring(1, 16 + 1).toByteArray();
    byte[] ciphertext      = joined.substring(16 + 1, joined.size() - 32).toByteArray();
    byte[] ivAndCiphertext = joined.substring(0, joined.size() - 32).toByteArray();
    byte[] mac             = joined.substring(joined.size() - 32).toByteArray();

    verifyMac(parts[1], ivAndCiphertext, mac);

    return ProvisionMessage.ADAPTER.decode(decrypt(parts[0], iv, ciphertext));
  }

  private void verifyMac(byte[] key, byte[] message, byte[] theirMac) {
    byte[] ourMac = getMac(key, message);
    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw new RuntimeException("Invalid MAC on provision message!");
    }
  }

  private byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      return cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }
}
