/**
 * Copyright (C) 2011 Whisper Systems
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

import javax.security.auth.Destroyable;
import java.util.Arrays;

/**
 * When a user first initializes TextSecure, a few secrets
 * are generated.  These are:
 *
 * 1) A 256bit symmetric encryption key.
 * 2) A 256bit symmetric MAC key.
 * 3) An ECC keypair.
 *
 * The first two, along with the identity ECC keypair, are
 * then encrypted on disk using PBE.
 *
 * This class represents 1 and 2.
 *
 * @author Moxie Marlinspike
 */

public class MasterSecret implements Cloneable, Destroyable, AutoCloseable {

  private SecureSecretKeySpec encryptionKey;
  private SecureSecretKeySpec macKey;

  public MasterSecret(byte[] encryptionKeyBytes, byte[] macKeyBytes) {
    this.encryptionKey = new SecureSecretKeySpec(encryptionKeyBytes, "AES");
    this.macKey        = new SecureSecretKeySpec(macKeyBytes, "HmacSHA256");

    // SecretKeySpec does an internal copy in its constructor.
    Arrays.fill(encryptionKeyBytes, (byte) 0x00);
    Arrays.fill(macKeyBytes, (byte)0x00);
  }


  public SecureSecretKeySpec getEncryptionKey() {
    return this.encryptionKey;
  }

  public SecureSecretKeySpec getMacKey() {
    return this.macKey;
  }

  @Override
  public MasterSecret clone() {
    MasterSecret other;
    try {
      other = (MasterSecret) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    other.encryptionKey = new SecureSecretKeySpec(encryptionKey.getEncoded(), encryptionKey.getAlgorithm());
    other.macKey        = new SecureSecretKeySpec(macKey.getEncoded(), macKey.getAlgorithm());
    return other;
  }

  @Override
  public void destroy() {
    if (encryptionKey != null) encryptionKey.destroy();
    if (macKey        != null) macKey.destroy();
  }

  @Override
  public boolean isDestroyed() {
    return (encryptionKey == null || encryptionKey.isDestroyed())
           && (macKey == null || macKey.isDestroyed());
  }

  @Override
  public void close() {
    destroy();
  }
}
