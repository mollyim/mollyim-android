package org.thoughtcrime.securesms.net;

import android.os.Build;

import org.thoughtcrime.securesms.util.Util;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

  public StandardUserAgentInterceptor() {
    super("Signal-Android " + Util.getCanonicalVersionName() + " (API " + Build.VERSION.SDK_INT + ")");
  }
}
