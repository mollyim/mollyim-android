/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;

/**
 * Class that handles encryption for local storage.
 *
 * The protocol format is roughly:
 *
 * 1) 16 byte random IV.
 * 2) AES-CBC(plaintext)
 * 3) HMAC-SHA256 of 1 and 2
 *
 * @author Moxie Marlinspike
 */

public class MasterCipher {

  private static final String TAG = Log.tag(MasterCipher.class);

  private final MasterSecret masterSecret;

  public MasterCipher(@NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  public byte[] encryptPrivateKey(ECPrivateKey privateKey) {
    return encrypt(privateKey.serialize(), "ECPrivateKey".getBytes());
  }

  public byte[] encryptPublicKey(ECPublicKey publicKey) {
    return encrypt(publicKey.serialize(), "ECPublicKey".getBytes());
  }

  public ECPrivateKey decryptPrivateKey(byte[] key) throws InvalidKeyException {
    try {
      return new ECPrivateKey(decrypt(key, "ECPrivateKey".getBytes()));
    } catch (GeneralSecurityException ge) {
      throw new InvalidKeyException(ge);
    }
  }

  public ECPublicKey decryptPublicKey(byte[] key) throws InvalidKeyException {
    try {
      return new ECPublicKey(decrypt(key, "ECPublicKey".getBytes()));
    } catch (GeneralSecurityException ge) {
      throw new InvalidKeyException(ge);
    }
  }

  public byte[] decrypt(@NonNull byte[] body) throws GeneralSecurityException {
    return decrypt(body, null);
  }

  public byte[] decrypt(@NonNull byte[] body, @Nullable byte[] ad) throws GeneralSecurityException {
    Mac mac = getMac(masterSecret.getMacKey());

    mac.update(ad);

    byte[] encryptedBody = verifyMacBody(mac, body);

    Cipher cipher = getDecryptingCipher(masterSecret.getEncryptionKey(), encryptedBody);

    return getDecryptedBody(cipher, encryptedBody);
  }

  public byte[] encrypt(byte[] body) {
    return encrypt(body, null);
  }

  public byte[] encrypt(@NonNull byte[] body, @Nullable byte[] ad) {
    try {
      Cipher cipher = getEncryptingCipher(masterSecret.getEncryptionKey());
      Mac    mac    = getMac(masterSecret.getMacKey());

      mac.update(ad);

      byte[] encryptedBody = getEncryptedBody(cipher, body);

      return getMacBody(mac, encryptedBody);
    } catch (GeneralSecurityException ge) {
      throw new AssertionError(ge);
    }
  }

  private byte[] verifyMacBody(@NonNull Mac hmac, @NonNull byte[] encryptedAndMac) throws GeneralSecurityException {
    if (encryptedAndMac.length < hmac.getMacLength()) {
      throw new GeneralSecurityException("length(encrypted body + MAC) < length(MAC)");
    }

    byte[] encrypted = new byte[encryptedAndMac.length - hmac.getMacLength()];
    System.arraycopy(encryptedAndMac, 0, encrypted, 0, encrypted.length);

    byte[] remoteMac = new byte[hmac.getMacLength()];
    System.arraycopy(encryptedAndMac, encryptedAndMac.length - remoteMac.length, remoteMac, 0, remoteMac.length);

    byte[] localMac = hmac.doFinal(encrypted);

    if (!MessageDigest.isEqual(remoteMac, localMac)) {
      throw new GeneralSecurityException("MAC doesen't match.");
    }

    return encrypted;
  }

  private byte[] getDecryptedBody(Cipher cipher, byte[] encryptedBody) throws IllegalBlockSizeException, BadPaddingException {
    return cipher.doFinal(encryptedBody, cipher.getBlockSize(), encryptedBody.length - cipher.getBlockSize());
  }

  private byte[] getEncryptedBody(Cipher cipher, byte[] body) throws IllegalBlockSizeException, BadPaddingException {
    byte[] encrypted = cipher.doFinal(body);
    byte[] iv        = cipher.getIV();

    byte[] ivAndBody = new byte[iv.length + encrypted.length];
    System.arraycopy(iv, 0, ivAndBody, 0, iv.length);
    System.arraycopy(encrypted, 0, ivAndBody, iv.length, encrypted.length);

    Arrays.fill(encrypted, (byte) 0);

    return ivAndBody;
  }

  private Mac getMac(SecureSecretKeySpec key) throws GeneralSecurityException {
    Mac hmac = Mac.getInstance("HmacSHA256");
    hmac.init(key);

    return hmac;
  }

  private byte[] getMacBody(Mac hmac, byte[] encryptedBody) {
    byte[] mac             = hmac.doFinal(encryptedBody);
    byte[] encryptedAndMac = new byte[encryptedBody.length + mac.length];

    System.arraycopy(encryptedBody, 0, encryptedAndMac, 0, encryptedBody.length);
    System.arraycopy(mac, 0, encryptedAndMac, encryptedBody.length, mac.length);

    return encryptedAndMac;
  }

  private Cipher getDecryptingCipher(SecureSecretKeySpec key, byte[] encryptedBody) throws GeneralSecurityException {
    Cipher          cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    IvParameterSpec iv     = new IvParameterSpec(encryptedBody, 0, cipher.getBlockSize());
    cipher.init(Cipher.DECRYPT_MODE, key, iv);

    return cipher;
  }

  private Cipher getEncryptingCipher(SecureSecretKeySpec key) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, key);

    return cipher;
  }

}
