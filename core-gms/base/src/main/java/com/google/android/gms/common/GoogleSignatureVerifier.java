package com.google.android.gms.common;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.util.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

class GoogleSignatureVerifier {

  private static final String TAG = "GoogleSignatureVerifier";

  /**
   * SHA-1: 38918a453d07199354f8b19af05ec6562ced5788
   * SHA-256: f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83
   * Certificate: CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US
   */
  private static final byte[] GMS_PRIMARY_SIGNATURE_SHA256 = new byte[] {
      (byte) 0xf0, (byte) 0xfd, (byte) 0x6c, (byte) 0x5b, (byte) 0x41, (byte) 0x0f, (byte) 0x25, (byte) 0xcb,
      (byte) 0x25, (byte) 0xc3, (byte) 0xb5, (byte) 0x33, (byte) 0x46, (byte) 0xc8, (byte) 0x97, (byte) 0x2f,
      (byte) 0xae, (byte) 0x30, (byte) 0xf8, (byte) 0xee, (byte) 0x74, (byte) 0x11, (byte) 0xdf, (byte) 0x91,
      (byte) 0x04, (byte) 0x80, (byte) 0xad, (byte) 0x6b, (byte) 0x2d, (byte) 0x60, (byte) 0xdb, (byte) 0x83
  };

  /**
   * SHA-1: 2169eddb5fbb1fdf241c262681024692c4fc1ecb
   * SHA-256: 5f2391277b1dbd489000467e4c2fa6af802430080457dce2f618992e9dfb5402
   * Certificate: CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US
   */
  private static final byte[] GMS_SECONDARY_SIGNATURE_SHA256 = new byte[] {
      (byte) 0x5f, (byte) 0x23, (byte) 0x91, (byte) 0x27, (byte) 0x7b, (byte) 0x1d, (byte) 0xbd, (byte) 0x48,
      (byte) 0x90, (byte) 0x00, (byte) 0x46, (byte) 0x7e, (byte) 0x4c, (byte) 0x2f, (byte) 0xa6, (byte) 0xaf,
      (byte) 0x80, (byte) 0x24, (byte) 0x30, (byte) 0x08, (byte) 0x04, (byte) 0x57, (byte) 0xdc, (byte) 0xe2,
      (byte) 0xf6, (byte) 0x18, (byte) 0x99, (byte) 0x2e, (byte) 0x9d, (byte) 0xfb, (byte) 0x54, (byte) 0x02
  };

  private static final byte[][] VALID_GMS_SIGNATURES = {
      GMS_PRIMARY_SIGNATURE_SHA256,
      GMS_SECONDARY_SIGNATURE_SHA256
  };

  private GoogleSignatureVerifier() {}

  public static boolean isGooglePublicSignedPackage(@NonNull PackageInfo packageInfo) {
    if (packageInfo.signatures != null) {
      if (packageInfo.signatures.length > 1) {
        Log.w(TAG, "Package has more than one signature.");
        return false;
      }
      return verifyGoogleSignature(packageInfo.signatures[0]);
    }
    return false;
  }

  private static boolean verifyGoogleSignature(@NonNull Signature signature) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] hash = sha256.digest(signature.toByteArray());
      for (byte[] valid : VALID_GMS_SIGNATURES) {
        if (Arrays.equals(hash, valid)) return true;
      }
      Log.w(TAG, "Unrecognized GMS signature: " + Hex.bytesToStringLowercase(hash));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return false;
  }
}
