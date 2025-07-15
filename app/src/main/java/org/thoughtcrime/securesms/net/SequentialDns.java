package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.util.Environment;
import org.thoughtcrime.securesms.util.NetworkUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import okhttp3.Dns;

/**
 * Iterates through an ordered list of {@link Dns}, trying each one in sequence.
 */
public class SequentialDns implements Dns {

  private static final String TAG = Log.tag(SequentialDns.class);

  private final List<Dns> dnsList;

  public SequentialDns(Dns... dns) {
    this.dnsList = Arrays.asList(dns);
  }

  @Override
  public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
    boolean isServiceHost = SignalServiceNetworkAccess.HOSTNAMES.contains(hostname);

    for (Dns dns : dnsList) {
      try {
        LinkedList<InetAddress> addresses = new LinkedList<>(dns.lookup(hostname));
        // MOLLY: Filter out local addresses for Signal service hosts, except in the Dev environment
        if (isServiceHost && !Environment.IS_DEV) {
          if (addresses.removeIf(InetAddress::isAnyLocalAddress)) {
            Log.w(TAG, "Ignore invalid address 0.0.0.0 while resolving hostname " + hostname);
          }
          if (addresses.removeIf(InetAddress::isLoopbackAddress)) {
            Log.w(TAG, "Ignore loopback address while resolving hostname " + hostname);
          }
        }
        if (!addresses.isEmpty()) {
          return addresses;
        } else {
          Log.w(TAG, String.format(Locale.ENGLISH, "Didn't find any addresses for %s using %s. Continuing.", hostname, dns.getClass().getSimpleName()));
        }
      } catch (UnknownHostException e) {
        Log.w(TAG, String.format(Locale.ENGLISH, "Failed to resolve %s using %s. Continuing. Network Type: %s", hostname, dns.getClass().getSimpleName(), NetworkUtil.getNetworkTypeDescriptor(AppDependencies.getApplication())));
      }
    }
    Log.w(TAG, "Failed to resolve using any DNS. Network Type: " + NetworkUtil.getNetworkTypeDescriptor(AppDependencies.getApplication()));
    throw new UnknownHostException(hostname);
  }
}
