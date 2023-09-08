package org.thoughtcrime.securesms.push;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.security.ProviderInstaller;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class AccountManagerFactory {

  private static AccountManagerFactory instance;
  public static AccountManagerFactory getInstance() {
    if (instance == null) {
      synchronized (AccountManagerFactory.class) {
        if (instance == null) {
          instance = new AccountManagerFactory();
        }
      }
    }
    return instance;
  }

  @VisibleForTesting
  public static void setInstance(@NonNull AccountManagerFactory accountManagerFactory) {
    synchronized (AccountManagerFactory.class) {
      instance = accountManagerFactory;
    }
  }
  private static final String TAG = Log.tag(AccountManagerFactory.class);

  public @NonNull SignalServiceAccountManager createAuthenticated(@NonNull Context context,
                                                                  @NonNull ACI aci,
                                                                  @NonNull PNI pni,
                                                                  @NonNull String e164,
                                                                  int deviceId,
                                                                  @NonNull String password)
  {
    if (ApplicationDependencies.getSignalServiceNetworkAccess().isCensored(e164)) {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (Throwable t) {
          Log.w(TAG, t);
        }
      });
    }

    return new SignalServiceAccountManager(ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(e164),
                                           aci,
                                           pni,
                                           e164,
                                           deviceId,
                                           password,
                                           BuildConfig.SIGNAL_AGENT,
                                           FeatureFlags.okHttpAutomaticRetry(),
                                           FeatureFlags.groupLimits().getHardLimit());
  }

  /**
   * Should only be used during registration when you haven't yet been assigned an ACI.
   */
  public @NonNull SignalServiceAccountManager createUnauthenticated(@NonNull Context context,
                                                                    @NonNull String e164,
                                                                    int deviceId,
                                                                    @NonNull String password)
  {
    if (new SignalServiceNetworkAccess(context).isCensored(e164)) {
      SignalExecutors.BOUNDED.execute(() -> {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (Throwable t) {
          Log.w(TAG, t);
        }
      });
    }

    return new SignalServiceAccountManager(ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(e164),
                                           null,
                                           null,
                                           e164,
                                           deviceId,
                                           password,
                                           BuildConfig.SIGNAL_AGENT,
                                           FeatureFlags.okHttpAutomaticRetry(),
                                           FeatureFlags.groupLimits().getHardLimit());
  }

  /**
   * Should only be used during registration when linking to an existing device.
   */
  public static @NonNull SignalServiceAccountManager createForDeviceLink(@NonNull Context context,
                                                                         @NonNull String password)
  {
    // Limitation - We cannot detect the need to use a censored configuration for the link process, because the number (and hence country code) is unknown.
    // Perhaps offer a UI to select just the country, and obtain censorship configuration that way?
    SignalServiceConfiguration configuration = ApplicationDependencies.getSignalServiceNetworkAccess().getConfiguration(null);
    return new SignalServiceAccountManager(configuration,
                                           new DynamicCredentialsProvider(null, null, null, password, SignalServiceAddress.DEFAULT_DEVICE_ID),
                                           BuildConfig.SIGNAL_AGENT,
                                           new GroupsV2Operations(ClientZkOperations.create(configuration), FeatureFlags.groupLimits().getHardLimit()),
                                           FeatureFlags.okHttpAutomaticRetry());
  }

}
