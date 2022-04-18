package org.thoughtcrime.securesms;

import android.app.Application;
import android.content.Context;

import androidx.test.runner.AndroidJUnitRunner;

import org.thoughtcrime.securesms.crypto.MasterSecretUtil;

public class AppTestRunner extends AndroidJUnitRunner {
  @Override
  public Application newApplication(ClassLoader cl, String className, Context context)
      throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    MasterSecretUtil.generateMasterSecret(context, MasterSecretUtil.getUnencryptedPassphrase());
    return super.newApplication(cl, className, context);
  }
}
