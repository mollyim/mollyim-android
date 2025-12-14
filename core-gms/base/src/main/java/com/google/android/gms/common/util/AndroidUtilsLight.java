package com.google.android.gms.common.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.wrappers.Wrappers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Keep
public class AndroidUtilsLight {

  AndroidUtilsLight() {}

  @Deprecated
  @Nullable
  public static byte[] getPackageCertificateHashBytes(@NonNull Context context, @NonNull String packageName)
      throws PackageManager.NameNotFoundException {
    PackageInfo pkgInfo =
        Wrappers.packageManager(context)
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);

    Signature[] signatures = pkgInfo.signatures;
    if (signatures != null && signatures.length == 1) {
      try {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        return sha1.digest(signatures[0].toByteArray());
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }

    return null;
  }
}
