package org.thoughtcrime.securesms.net;

import android.os.Build;

import org.thoughtcrime.securesms.util.Util;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

  // MOLLY: Replace BuildConfig.VERSION_NAME by Util.getSignalCanonicalVersionName()
  public static final String USER_AGENT = "Signal-Android/" + Util.getSignalCanonicalVersionName() + " Android/" + Build.VERSION.SDK_INT;

  public StandardUserAgentInterceptor() {
    super(USER_AGENT);
  }
}
