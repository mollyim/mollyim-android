package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public final class ProxyValues extends SignalStoreValues {

  private static final String  KEY_PROXY_ENABLED = "proxy.enabled";
  private static final String  KEY_HOST          = "proxy.host";
  private static final String  KEY_PORT          = "proxy.port";

  ProxyValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(KEY_PROXY_ENABLED, KEY_HOST, KEY_PORT);
  }

  // MOLLY: Replaced by NetworkManager
}
