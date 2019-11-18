/*
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
import android.content.SharedPreferences.Editor;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.backup.BackupProtos;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  @SuppressWarnings("unused")
  private static final String TAG = IdentityKeyUtil.class.getSimpleName();

  private static final String IDENTITY_PUBLIC_KEY_PREF                    = "pref_identity_public_v3";
  private static final String IDENTITY_PRIVATE_KEY_PREF                   = "pref_identity_private_v3";

  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return
        preferences.contains(IDENTITY_PUBLIC_KEY_PREF) &&
        preferences.contains(IDENTITY_PRIVATE_KEY_PREF);
  }

  public static @NonNull IdentityKey getIdentityKey(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      byte[] publicKeyBytes     = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_PREF));
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      ECPublicKey  publicKey    = masterCipher.decryptPublicKey(publicKeyBytes);
      return new IdentityKey(publicKey);
    } catch (IOException | InvalidKeyException e) {
      throw new SecurityException(e);
    }
  }

  public static @NonNull IdentityKeyPair getIdentityKeyPair(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      byte[] privateKeyBytes    = Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_PREF));
      IdentityKey  identityKey  = getIdentityKey(context, masterSecret);
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      ECPrivateKey privateKey   = masterCipher.decryptPrivateKey(privateKeyBytes);
      return new IdentityKeyPair(identityKey, privateKey);
    } catch (IOException | InvalidKeyException e) {
      throw new SecurityException(e);
    }
  }

  public static @NonNull IdentityKey getIdentityKey(@NonNull Context context) {
    try (MasterSecret masterSecret = KeyCachingService.getMasterSecret()) {
      return getIdentityKey(context, masterSecret);
    }
  }

  public static @NonNull IdentityKeyPair getIdentityKeyPair(@NonNull Context context) {
    try (MasterSecret masterSecret = KeyCachingService.getMasterSecret()) {
      return getIdentityKeyPair(context, masterSecret);
    }
  }

  public static void generateIdentityKeys(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    ECKeyPair    identityKeyPair = Curve.generateKeyPair();
    ECPublicKey  publicKey       = identityKeyPair.getPublicKey();
    ECPrivateKey privateKey      = identityKeyPair.getPrivateKey();

    MasterCipher masterCipher    = new MasterCipher(masterSecret);

    save(context, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(masterCipher.encryptPublicKey(publicKey)));
    save(context, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(masterCipher.encryptPrivateKey(privateKey)));
  }

  public static List<BackupProtos.SharedPreference> getBackupRecord(@NonNull Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return new LinkedList<BackupProtos.SharedPreference>() {{
      add(BackupProtos.SharedPreference.newBuilder()
                                       .setFile(MasterSecretUtil.PREFERENCES_NAME)
                                       .setKey(IDENTITY_PUBLIC_KEY_PREF)
                                       .setValue(preferences.getString(IDENTITY_PUBLIC_KEY_PREF, null))
                                       .build());
      add(BackupProtos.SharedPreference.newBuilder()
                                       .setFile(MasterSecretUtil.PREFERENCES_NAME)
                                       .setKey(IDENTITY_PRIVATE_KEY_PREF)
                                       .setValue(preferences.getString(IDENTITY_PRIVATE_KEY_PREF, null))
                                       .build());
    }};
  }

  private static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    return preferences.getString(key, null);
  }

  private static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    preferencesEditor.putString(key, value);
    if (!preferencesEditor.commit()) throw new AssertionError("failed to save identity key/value to shared preferences");
  }

}
