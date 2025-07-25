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
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.Arrays;
import java.util.UUID;

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

  private static final char[] UNENCRYPTED_PASSPHRASE = "unencrypted".toCharArray();

  private static final String PREFERENCES_NAME = "MasterKeys";

  private static final String KEY_ALIAS_DEFAULT = "MollySecret";
  private static final String KEY_KEYSTORE_ALIAS = "keystore_alias";
  private static final String KEY_PASSPHRASE_SALT = "passphrase_salt";
  private static final String KEY_KDF_PARAMETERS = "kdf_parameters";
  private static final String KEY_KDF_ELAPSED = "kdf_elapsed";
  private static final String KEY_MASTER_SECRET = "master_secret";
  private static final String KEY_ENCRYPTION_IV = "encryption_iv";
  private static final String KEY_KEYSTORE_INITIALIZED = "keystore_initialized";
  private static final String KEY_PASSPHRASE_INITIALIZED = "passphrase_initialized";

  private static final String KEY_DURESS_ALIAS_DEFAULT = "MollyDuressSecret";
  private static final String KEY_DURESS_KEYSTORE_ALIAS = "duress_keystore_alias";
  private static final String KEY_DURESS_PASSPHRASE_SALT = "duress_passphrase_salt";
  private static final String KEY_DURESS_KDF_PARAMETERS = "duress_kdf_parameters";
  private static final String KEY_DURESS_KDF_ELAPSED = "duress_kdf_elapsed";
  private static final String KEY_DURESS_MASTER_SECRET = "duress_master_secret";
  private static final String KEY_DURESS_ENCRYPTION_IV = "duress_encryption_iv";
  private static final String KEY_DURESS_KEYSTORE_INITIALIZED = "duress_keystore_initialized";
  private static final String KEY_DURESS_PASSPHRASE_INITIALIZED = "duress_passphrase_initialized";

  private static final String ASYMMETRIC_LOCAL_PUBLIC_DJB   = "asymmetric_master_secret_curve25519_public";
  private static final String ASYMMETRIC_LOCAL_PRIVATE_DJB  = "asymmetric_master_secret_curve25519_private";

  private static void changeMasterSecretPassphrase(Context context,
                                                   MasterSecret masterSecret,
                                                   char[] newPassphrase) {
    changeMasterSecretPassphrase(context, masterSecret, newPassphrase, false /* isDuress */);
  }

  private static void changeMasterSecretPassphrase(Context context,
                                                   MasterSecret masterSecret,
                                                   char[] newPassphrase,
                                                   boolean isDuress)
  {
    SharedPreferences.Editor prefs = getSharedPreferences(context).edit();

    String keyStoreAlias = null;
    String savedKeyStoreAlias = null;
    if (isDuress) {
      savedKeyStoreAlias = retrieve(context, KEY_DURESS_KEYSTORE_ALIAS,KEY_DURESS_ALIAS_DEFAULT);
    } else {
      savedKeyStoreAlias = retrieve(context, KEY_KEYSTORE_ALIAS, KEY_ALIAS_DEFAULT);
    }

    SecureSecretKeySpec secretKey;

    if (!isDuress && isUnencryptedPassphrase(newPassphrase)) {
      secretKey = getUnencryptedKey();
    } else {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      System.gc();

      kdf.findParameters(Util.getAvailMemory(context) / 2);

      keyStoreAlias = UUID.randomUUID().toString();
      kdf.setHmacKey(KeyStoreHelper.createKeyStoreEntryHmac(keyStoreAlias, hasStrongBox(context)));

      byte[] passphraseSalt = generateSalt();

      String keyPassPhraseSalt = isDuress ? KEY_DURESS_PASSPHRASE_SALT : KEY_PASSPHRASE_SALT;
      String keyKdfParameters = isDuress ? KEY_DURESS_KDF_PARAMETERS : KEY_KDF_PARAMETERS;
      String keyKdfElapsed = isDuress ? KEY_DURESS_KDF_ELAPSED : KEY_KDF_ELAPSED;
      prefs.putString(keyPassPhraseSalt, Base64.encodeWithPadding(passphraseSalt));
      prefs.putString(keyKdfParameters, kdf.getParameters());
      prefs.putLong(keyKdfElapsed, kdf.getElapsedTimeMillis());

      secretKey = kdf.deriveKey(newPassphrase, passphraseSalt);
    }

    byte[] encryptionIV          = generateIV();
    byte[] combinedSecrets       = Util.combine(masterSecret.getEncryptionKey().getEncoded(),
                                                masterSecret.getMacKey().getEncoded());
    byte[] encryptedMasterSecret = encrypt(encryptionIV, combinedSecrets, secretKey);

    Arrays.fill(combinedSecrets, (byte) 0);
    secretKey.destroy();

    String keyEncryptionIv = isDuress ? KEY_DURESS_ENCRYPTION_IV : KEY_ENCRYPTION_IV;
    String keyMasterSecret = isDuress ? KEY_DURESS_MASTER_SECRET : KEY_MASTER_SECRET;
    String keyPassphraseInitialized = isDuress ? KEY_DURESS_PASSPHRASE_INITIALIZED : KEY_PASSPHRASE_INITIALIZED;
    String keyKeystoreInitialized = isDuress ? KEY_DURESS_KEYSTORE_INITIALIZED : KEY_KEYSTORE_INITIALIZED;
    String keyKeystoreAlias = isDuress ? KEY_DURESS_KEYSTORE_ALIAS : KEY_KEYSTORE_ALIAS;
    prefs.putString(keyEncryptionIv, Base64.encodeWithPadding(encryptionIV));
    prefs.putString(keyMasterSecret, Base64.encodeWithPadding(encryptedMasterSecret));
    prefs.putBoolean(keyPassphraseInitialized, true);
    prefs.putBoolean(keyKeystoreInitialized, keyStoreAlias != null);
    prefs.putString(keyKeystoreAlias, keyStoreAlias);

    if (!prefs.commit()) {
      throw new AssertionError("failed to save preferences in MasterSecretUtil");
    }

    if (savedKeyStoreAlias != null) {
      KeyStoreHelper.deleteKeyStoreEntry(savedKeyStoreAlias);
    }
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
      throws InvalidPassphraseException, UnrecoverableKeyException {
    return getMasterSecret(context, passphrase, false);
  }

  public static MasterSecret getMasterSecret(Context context, char[] passphrase, boolean isDuress)
      throws InvalidPassphraseException, UnrecoverableKeyException
  {
    SecureSecretKeySpec secretKey;

    String keyPassphraseSalt = isDuress ? KEY_DURESS_PASSPHRASE_SALT : KEY_PASSPHRASE_SALT;
    String keyKdfParams = isDuress ? KEY_DURESS_KDF_PARAMETERS : KEY_KDF_PARAMETERS;
    String keyMasterSecret = isDuress ? KEY_DURESS_MASTER_SECRET : KEY_MASTER_SECRET;
    String keyEncryptionIv = isDuress ? KEY_DURESS_ENCRYPTION_IV : KEY_ENCRYPTION_IV;
    String keyKeystoreInitialized = isDuress ? KEY_DURESS_KEYSTORE_INITIALIZED : KEY_KEYSTORE_INITIALIZED;
    String keyKeystoreAlias = isDuress ? KEY_DURESS_KEYSTORE_ALIAS : KEY_KEYSTORE_ALIAS;
    String defaultKeyAlias = isDuress ? KEY_DURESS_ALIAS_DEFAULT : KEY_ALIAS_DEFAULT;
    byte[]  passphraseSalt        = retrieve(context, keyPassphraseSalt);
    String  serializedParams      = retrieve(context, keyKdfParams, "");
    byte[]  encryptedMasterSecret = retrieve(context, keyMasterSecret);
    byte[]  encryptionIV          = retrieve(context, keyEncryptionIv);
    boolean hasKeyStoreSecret     = retrieve(context, keyKeystoreInitialized, false);
    String  keyStoreAlias         = retrieve(context, keyKeystoreAlias, defaultKeyAlias);

    if (!isDuress && isUnencryptedPassphrase(passphrase)) {
      secretKey = getUnencryptedKey();
    } else {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      try {
        kdf.setParameters(serializedParams);
      } catch (Exception e) {
        // Nothing.
      }

      if (hasKeyStoreSecret) {
        try {
          kdf.setHmacKey(KeyStoreHelper.getKeyStoreEntryHmac(keyStoreAlias));
        } catch (UnrecoverableEntryException e) {
          throw new UnrecoverableKeyException(e);
        }
      }

      secretKey = kdf.deriveKey(passphrase, passphraseSalt);
    }

    try {
      byte[] combinedSecrets  = decrypt(encryptionIV, encryptedMasterSecret, secretKey);
      byte[] encryptionSecret = Util.split(combinedSecrets, 32, 32)[0];
      byte[] macSecret        = Util.split(combinedSecrets, 32, 32)[1];

      Arrays.fill(combinedSecrets, (byte) 0);

      MasterSecret masterSecret = new MasterSecret(encryptionSecret, macSecret);

      Arrays.fill(encryptionSecret, (byte) 0);
      Arrays.fill(macSecret, (byte) 0);

      if (!isDuress && !hasKeyStoreSecret && !isUnencryptedPassphrase(passphrase)) {
        // OS upgraded to API 23 or above
        Log.i(TAG, "KeyStore is available. Forcing master secret re-encryption to use it.");
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
    ECKeyPair    keyPair      = ECKeyPair.generate();

    if (!getSharedPreferences(context).edit()
        .putString(ASYMMETRIC_LOCAL_PUBLIC_DJB, Base64.encodeWithPadding(masterCipher.encryptPublicKey(keyPair.getPublicKey())))
        .putString(ASYMMETRIC_LOCAL_PRIVATE_DJB, Base64.encodeWithPadding(masterCipher.encryptPrivateKey(keyPair.getPrivateKey())))
        .commit()) {
      throw new AssertionError("failed to save preferences in MasterSecretUtil");
    }

    return new AsymmetricMasterSecret(keyPair.getPublicKey(), keyPair.getPrivateKey());
  }

  public static MasterSecret generateMasterSecret(Context context, char[] passphrase) {
    return generateMasterSecret(context, passphrase, false);
  }
  public static MasterSecret generateMasterSecret(Context context, char[] passphrase, boolean isDuress) {
    byte[]       encryptionSecret = generateEncryptionSecret();
    byte[]       macSecret        = generateMacSecret();
    MasterSecret masterSecret     = new MasterSecret(encryptionSecret, macSecret);

    Arrays.fill(encryptionSecret, (byte) 0x00);
    Arrays.fill(macSecret, (byte)0x00);

    changeMasterSecretPassphrase(context, masterSecret, passphrase, isDuress);

    return masterSecret;
  }

  private static byte[] retrieve(Context context, String key) {
    SharedPreferences settings = getSharedPreferences(context);
    String encodedValue        = settings.getString(key, "");

    try {
      if (encodedValue == null) return new byte[0];
      else                      return Base64.decode(encodedValue);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String retrieve(Context context, String key, String defaultValue) {
    SharedPreferences settings = getSharedPreferences(context);
    return settings.getString(key, defaultValue);
  }

  private static boolean retrieve(Context context, String key, boolean defaultValue) {
    SharedPreferences settings = getSharedPreferences(context);
    return settings.getBoolean(key, defaultValue);
  }

  private static long retrieve(Context context, String key, long defaultValue) {
    SharedPreferences settings = getSharedPreferences(context);
    return settings.getLong(key, defaultValue);
  }

  public static long getKdfElapsedTimeMillis(Context context) {
    return retrieve(context, KEY_KDF_ELAPSED, 0);
  }

  public static boolean isPassphraseInitialized(Context context) {
    return retrieve(context, KEY_PASSPHRASE_INITIALIZED, false);
  }

  public static boolean isDuressCodeInitialized(Context context) {
    return retrieve(context, KEY_DURESS_PASSPHRASE_INITIALIZED, false);
  }

  public static void setDuressCodeInitialized(Context context, boolean value) {
    SharedPreferences.Editor prefs = getSharedPreferences(context).edit();
    prefs.putBoolean(KEY_DURESS_PASSPHRASE_INITIALIZED, value);
    prefs.commit();
  }

  public static boolean isKeyStoreInitialized(Context context) {
    return retrieve(context, KEY_KEYSTORE_INITIALIZED, false);
  }

  public static char[] getUnencryptedPassphrase() {
    return UNENCRYPTED_PASSPHRASE.clone();
  }

  private static boolean isUnencryptedPassphrase(char[] passphrase) {
    return Arrays.equals(UNENCRYPTED_PASSPHRASE, passphrase);
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

  private static boolean hasStrongBox(final Context context) {
    return Build.VERSION.SDK_INT >= 28 &&
           context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
  }

  private static SharedPreferences getSharedPreferences(@NonNull Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }
}
