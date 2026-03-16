package org.thoughtcrime.securesms.net;

import android.os.Build;

import im.molly.app.base.ApkInfo;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

  public static final String USER_AGENT = "Signal-Android/" + ApkInfo.signalCanonicalVersionName + " Android/" + Build.VERSION.SDK_INT;

  public StandardUserAgentInterceptor() {
    super(USER_AGENT);
  }
}
