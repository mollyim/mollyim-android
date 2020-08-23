package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

public final class PlayStoreUtil {

  private PlayStoreUtil() {
  }

  public static void openPlayStoreOrOurApkDownloadPage(@NonNull Context context) {
    CommunicationActions.openBrowserLink(context, context.getString(R.string.install_url));
  }
}
