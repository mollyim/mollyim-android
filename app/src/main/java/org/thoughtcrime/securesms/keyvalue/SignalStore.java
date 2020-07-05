package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceDataStore;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.SignalUncaughtExceptionHandler;

/**
 * Simple, encrypted key-value store.
 */
public final class SignalStore {

  private static SignalStore instance;

  private final KeyValueStore        store;
  private final KbsValues            kbsValues;
  private final RegistrationValues   registrationValues;
  private final PinValues            pinValues;
  private final RemoteConfigValues   remoteConfigValues;
  private final StorageServiceValues storageServiceValues;
  private final UiHints              uiHints;
  private final TooltipValues        tooltipValues;
  private final MiscellaneousValues  misc;

  private SignalStore() {
    this.store                = ApplicationDependencies.getKeyValueStore();
    this.kbsValues            = new KbsValues(store);
    this.registrationValues   = new RegistrationValues(store);
    this.pinValues            = new PinValues(store);
    this.remoteConfigValues   = new RemoteConfigValues(store);
    this.storageServiceValues = new StorageServiceValues(store);
    this.uiHints              = new UiHints(store);
    this.tooltipValues        = new TooltipValues(store);
    this.misc                 = new MiscellaneousValues(store);
  }

  public static SignalStore getInstance() {
    if (instance == null) {
      synchronized (SignalStore.class) {
        if (instance == null) {
          instance = new SignalStore();
        }
      }
    }
    return instance;
  }

  public static void onFirstEverAppLaunch() {
    kbsValues().onFirstEverAppLaunch();
    registrationValues().onFirstEverAppLaunch();
    pinValues().onFirstEverAppLaunch();
    remoteConfigValues().onFirstEverAppLaunch();
    storageServiceValues().onFirstEverAppLaunch();
    uiHints().onFirstEverAppLaunch();
    tooltips().onFirstEverAppLaunch();
    misc().onFirstEverAppLaunch();
  }

  public static @NonNull KbsValues kbsValues() {
    return getInstance().kbsValues;
  }

  public static @NonNull RegistrationValues registrationValues() {
    return getInstance().registrationValues;
  }

  public static @NonNull PinValues pinValues() {
    return getInstance().pinValues;
  }

  public static @NonNull RemoteConfigValues remoteConfigValues() {
    return getInstance().remoteConfigValues;
  }

  public static @NonNull StorageServiceValues storageServiceValues() {
    return getInstance().storageServiceValues;
  }

  public static @NonNull UiHints uiHints() {
    return getInstance().uiHints;
  }

  public static @NonNull TooltipValues tooltips() {
    return getInstance().tooltipValues;
  }

  public static @NonNull MiscellaneousValues misc() {
    return getInstance().misc;
  }

  public static @NonNull GroupsV2AuthorizationSignalStoreCache groupsV2AuthorizationCache() {
    return new GroupsV2AuthorizationSignalStoreCache(getStore());
  }

  public static @NonNull PreferenceDataStore getPreferenceDataStore() {
    return new SignalPreferenceDataStore(getStore());
  }

  /**
   * Ensures any pending writes are finished. Only intended to be called by
   * {@link SignalUncaughtExceptionHandler}.
   */
  public static void blockUntilAllWritesFinished() {
    getStore().blockUntilAllWritesFinished();
  }

  private static @NonNull KeyValueStore getStore() {
    return getInstance().store;
  }
}
