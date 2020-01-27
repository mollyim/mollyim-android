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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Helper class for generating and securely storing a MasterSecret.
 *
 * @author Moxie Marlinspike
 */

public class MasterSecretUtil {

  private static final String TAG = Log.tag(MasterSecretUtil.class);

  public static final char[] UNENCRYPTED_PASSPHRASE = "unencrypted".toCharArray();

  private static final String PREFERENCES_NAME = "MasterKeys";

  private static final String ASYMMETRIC_LOCAL_PUBLIC_DJB   = "asymmetric_master_secret_curve25519_public";
  private static final String ASYMMETRIC_LOCAL_PRIVATE_DJB  = "asymmetric_master_secret_curve25519_private";

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          MasterSecret masterSecret,
                                                          char[] newPassphrase)
  {
    SecureSecretKeySpec secretKey;

    if (!isUnencryptedPassphrase(newPassphrase)) {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      kdf.findParameters(Util.getAvailMemory(context) / 2);

      if (isDeviceSecure(context)) {
        kdf.setSecretKey(KeyStoreHelper.createKeyStoreEntryHmac());
      }

      byte[] passphraseSalt = generateSalt();

      if (!context.getSharedPreferences(PREFERENCES_NAME, 0).edit()
          .putString("passphrase_salt", Base64.encodeBytes(passphraseSalt))
          .putString("kdf_parameters", kdf.getParameters())
          .putLong("kdf_elapsed", kdf.getElapsedTimeMillis())
          .putBoolean("keystore_initialized", kdf.getSecretKey() != null)
          .commit()) {
        throw new AssertionError("failed to save preferences in MasterSecretUtil");
      }

      secretKey = kdf.deriveKey(passphraseSalt, newPassphrase);
    } else {
      secretKey = getUnencryptedKey();
    }

    byte[] encryptionIV          = generateIV();
    byte[] combinedSecrets       = Util.combine(masterSecret.getEncryptionKey().getEncoded(),
                                                masterSecret.getMacKey().getEncoded());
    byte[] encryptedMasterSecret = encrypt(encryptionIV, combinedSecrets, secretKey);

    Arrays.fill(combinedSecrets, (byte) 0);
    secretKey.destroy();

    if (!context.getSharedPreferences(PREFERENCES_NAME, 0).edit()
        .putString("encryption_iv", Base64.encodeBytes(encryptionIV))
        .putString("master_secret", Base64.encodeBytes(encryptedMasterSecret))
        .putBoolean("passphrase_initialized", true)
        .commit()) {
      throw new AssertionError("failed to save preferences in MasterSecretUtil");
    }

    return masterSecret;
  }

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          char[] originalPassphrase,
                                                          char[] newPassphrase)
      throws InvalidPassphraseException, UnrecoverableKeyException
  {
    MasterSecret masterSecret = getMasterSecret(context, originalPassphrase);
    changeMasterSecretPassphrase(context, masterSecret, newPassphrase);

    return masterSecret;
  }

  public static MasterSecret getMasterSecret(Context context, char[] passphrase)
      throws InvalidPassphraseException, UnrecoverableKeyException
  {
    SecureSecretKeySpec secretKey;

    byte[]  passphraseSalt        = retrieve(context, "passphrase_salt");
    String  serializedParams      = retrieve(context, "kdf_parameters", "");
    byte[]  encryptedMasterSecret = retrieve(context, "master_secret");
    byte[]  encryptionIV          = retrieve(context, "encryption_iv");
    boolean hasKeyStoreSecret     = retrieve(context, "keystore_initialized", false);

    if (!isUnencryptedPassphrase(passphrase))
    {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      kdf.setParameters(serializedParams);

      if (hasKeyStoreSecret) {
        if (isDeviceSecure(context)) {
          try {
            kdf.setSecretKey(KeyStoreHelper.getKeyStoreEntryHmac());
          } catch (UnrecoverableEntryException e) {
            throw new UnrecoverableKeyException(e);
          }
        } else {
          throw new UnrecoverableKeyException("OS downgrade not supported. KeyStore secret exists on platform < M!");
        }
      }

      secretKey = kdf.deriveKey(passphraseSalt, passphrase);
    } else {
      secretKey = getUnencryptedKey();
    }

    try {
      byte[] combinedSecrets  = decrypt(encryptionIV, encryptedMasterSecret, secretKey);
      byte[] encryptionSecret = Util.split(combinedSecrets, 32, 32)[0];
      byte[] macSecret        = Util.split(combinedSecrets, 32, 32)[1];

      Arrays.fill(combinedSecrets, (byte) 0);

      MasterSecret masterSecret = new MasterSecret(encryptionSecret, macSecret);

      if (!hasKeyStoreSecret && isDeviceSecure(context) && isUnencryptedPassphrase(passphrase)) {
        Log.i(TAG, "KeyStore is available and secure. Forcing master secret re-encryption to use it.");
        changeMasterSecretPassphrase(context, masterSecret, passphrase);
      }

      return masterSecret;
    } catch (BadPaddingException bpe) {
      throw new InvalidPassphraseException(bpe);
    } finally {
      secretKey.destroy();
    }
  }

  public static AsymmetricMasterSecret getAsymmetricMasterSecret(@NonNull  Context context,
                                                                 @Nullable MasterSecret masterSecret)
  {
    try {
      byte[] djbPublicBytes   = retrieve(context, ASYMMETRIC_LOCAL_PUBLIC_DJB);
      byte[] djbPrivateBytes  = retrieve(context, ASYMMETRIC_LOCAL_PRIVATE_DJB);

      MasterCipher masterCipher  = new MasterCipher(masterSecret);
      ECPublicKey  djbPublicKey  = masterCipher.decryptPublicKey(djbPublicBytes);
      ECPrivateKey djbPrivateKey = masterCipher.decryptPrivateKey(djbPrivateBytes);

      return new AsymmetricMasterSecret(djbPublicKey, djbPrivateKey);
    } catch (InvalidKeyException ike) {
      throw new SecurityException(ike);
    }
  }

  public static AsymmetricMasterSecret generateAsymmetricMasterSecret(Context context,
                                                                      MasterSecret masterSecret)
  {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    ECKeyPair    keyPair      = Curve.generateKeyPair();

    if (!context.getSharedPreferences(PREFERENCES_NAME, 0).edit()
        .putString(ASYMMETRIC_LOCAL_PUBLIC_DJB, Base64.encodeBytes(masterCipher.encryptPublicKey(keyPair.getPublicKey())))
        .putString(ASYMMETRIC_LOCAL_PRIVATE_DJB, Base64.encodeBytes(masterCipher.encryptPrivateKey(keyPair.getPrivateKey())))
        .commit()) {
      throw new AssertionError("failed to save preferences in MasterSecretUtil");
    }

    return new AsymmetricMasterSecret(keyPair.getPublicKey(), keyPair.getPrivateKey());
  }

  public static MasterSecret generateMasterSecret(Context context, char[] passphrase) {
    byte[]       encryptionSecret = generateEncryptionSecret();
    byte[]       macSecret        = generateMacSecret();
    MasterSecret masterSecret     = new MasterSecret(encryptionSecret, macSecret);

    return changeMasterSecretPassphrase(context, masterSecret, passphrase);
  }

  private static byte[] retrieve(Context context, String key) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    String encodedValue        = settings.getString(key, "");

    try {
      if (encodedValue == null) return new byte[0];
      else                      return Base64.decode(encodedValue);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String retrieve(Context context, String key, String defaultValue) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    return settings.getString(key, defaultValue);
  }

  private static boolean retrieve(Context context, String key, boolean defaultValue) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    return settings.getBoolean(key, defaultValue);
  }

  private static long retrieve(Context context, String key, long defaultValue) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    return settings.getLong(key, defaultValue);
  }

  public static long getKdfElapsedTimeMillis(Context context) {
    return retrieve(context, "kdf_elapsed", 0);
  }

  public static boolean isPassphraseInitialized(Context context) {
    return retrieve(context, "passphrase_initialized", false);
  }

  public static boolean isKeyStoreInitialized(Context context) {
    return retrieve(context, "keystore_initialized", false);
  }

  private static boolean isDeviceSecure(Context context) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && ServiceUtil.getKeyguardManager(context).isDeviceSecure();
  }

  private static boolean isUnencryptedPassphrase(char[] passphrase) {
    return passphrase == UNENCRYPTED_PASSPHRASE;
  }

  private static SecureSecretKeySpec getUnencryptedKey() {
    return new SecureSecretKeySpec(new byte[32], "AES");
  }

  private static byte[] generateEncryptionSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);

      SecretKey key = generator.generateKey();
      return key.getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] generateMacSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");

      SecretKey key = generator.generateKey();
      return key.getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] generateSalt() {
    return Util.getSecretBytes(16);
  }

  private static byte[] generateIV() {
    return Util.getSecretBytes(12);
  }

  private static byte[] encrypt(byte[] iv, byte[] data, SecretKey secretKey) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      return cipher.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] decrypt(byte[] iv, byte[] data, SecretKey secretKey) throws BadPaddingException {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      return cipher.doFinal(data);
    } catch (BadPaddingException bpe) {
      throw bpe;
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }
}
