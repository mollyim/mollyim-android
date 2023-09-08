package org.whispersystems.signalservice.api.util;

import com.google.protobuf.ByteString;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.whispersystems.signalservice.internal.devices.DeviceNameProtos.DeviceName;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DeviceNameUtil {
  public static String encryptDeviceName(String plainTextDeviceName, ECPrivateKey identityKey) {
    final byte[] plainText = plainTextDeviceName.getBytes(StandardCharsets.UTF_8);

    ECKeyPair ephemeralKeyPair = Curve.generateKeyPair();

    try {
      Mac    mac    = Mac.getInstance("HmacSHA256");
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

      byte[] masterSecret = Curve.calculateAgreement(ephemeralKeyPair.getPublicKey(), identityKey);

      mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
      byte[] key1 = mac.doFinal("auth".getBytes());

      mac.init(new SecretKeySpec(key1, "HmacSHA256"));
      byte[] syntheticIv = ByteUtil.trim(mac.doFinal(plainText), 16);

      mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
      byte[] key2 = mac.doFinal("cipher".getBytes());

      mac.init(new SecretKeySpec(key2, "HmacSHA256"));
      byte[] cipherKey = mac.doFinal(syntheticIv);

      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(new byte[16]));
      final byte[] cipherText = cipher.doFinal(plainText);

      final DeviceName deviceName = DeviceName
          .newBuilder()
          .setCiphertext(ByteString.copyFrom(cipherText))
          .setEphemeralPublic(ByteString.copyFrom(ephemeralKeyPair.getPublicKey().serialize()))
          .setSyntheticIv(ByteString.copyFrom(syntheticIv))
          .build();

      return Base64.encodeBytes(deviceName.toByteArray());
    } catch (InvalidKeyException | GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }

  public static String decryptDeviceName(String encryptedDeviceName, ECPrivateKey identityKey) throws IOException {
    if (Util.isEmpty(encryptedDeviceName) || encryptedDeviceName.length() < 4) {
      throw new IOException("Invalid DeviceInfo name.");
    }

    DeviceName deviceName = DeviceName.parseFrom(Base64.decode(encryptedDeviceName));

    if (!deviceName.hasCiphertext() || !deviceName.hasEphemeralPublic() || !deviceName.hasSyntheticIv()) {
      throw new IOException("Got a DeviceName that wasn't properly populated.");
    }

    byte[] syntheticIv          = deviceName.getSyntheticIv().toByteArray();
    byte[] cipherText           = deviceName.getCiphertext().toByteArray();
    byte[] ephemeralPublicBytes = deviceName.getEphemeralPublic().toByteArray();

    try {
      ECPublicKey ephemeralPublic = Curve.decodePoint(ephemeralPublicBytes, 0);
      byte[]      masterSecret    = Curve.calculateAgreement(ephemeralPublic, identityKey);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
      byte[] cipherKeyPart1 = mac.doFinal("cipher".getBytes());

      mac.init(new SecretKeySpec(cipherKeyPart1, "HmacSHA256"));
      byte[] cipherKey = mac.doFinal(syntheticIv);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(new byte[16]));
      final byte[] plaintext = cipher.doFinal(cipherText);

      mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
      byte[] verificationPart1 = mac.doFinal("auth".getBytes());

      mac.init(new SecretKeySpec(verificationPart1, "HmacSHA256"));
      byte[] verificationPart2 = mac.doFinal(plaintext);
      byte[] ourSyntheticIv    = ByteUtil.trim(verificationPart2, 16);

      if (!MessageDigest.isEqual(ourSyntheticIv, syntheticIv)) {
        throw new GeneralSecurityException("The computed syntheticIv didn't match the actual syntheticIv.");
      }

      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (InvalidKeyException | GeneralSecurityException e) {
      throw new IOException("Failed to decrypt device name", e);
    }
  }
}
