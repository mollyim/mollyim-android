package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SvrValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import im.molly.unifiedpush.helper.UnifiedPushHelper;

public class RefreshAttributesJob extends BaseJob {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = Log.tag(RefreshAttributesJob.class);

  private static final String KEY_FORCED = "forced";

  private static volatile boolean hasRefreshedThisAppCycle;

  private final boolean forced;

  public RefreshAttributesJob() {
    this(true);
  }

  /**
   * @param forced True if you want this job to run no matter what. False if you only want this job
   *               to run if it hasn't run yet this app cycle.
   */
  public RefreshAttributesJob(boolean forced) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("RefreshAttributesJob")
                           .setMaxInstancesForFactory(2)
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         forced);
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters, boolean forced) {
    super(parameters);
    this.forced = forced;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putBoolean(KEY_FORCED, forced).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getE164() == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    if (!forced && hasRefreshedThisAppCycle) {
      Log.d(TAG, "Already refreshed this app cycle. Skipping.");
      return;
    }

    int       registrationId              = SignalStore.account().getRegistrationId();
    boolean   fetchesMessages             = !UnifiedPushHelper.isPushEnabled() || SignalStore.internalValues().isWebsocketModeForced();
    byte[]    unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean   universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    String    registrationLockV2          = null;
    SvrValues svrValues                   = SignalStore.svr();
    int       pniRegistrationId           = new RegistrationRepository(ApplicationDependencies.getApplication()).getPniRegistrationId();
    String    recoveryPassword            = svrValues.getRecoveryPassword();

    if (svrValues.isRegistrationLockEnabled()) {
      registrationLockV2 = svrValues.getRegistrationLockToken();
    }

    boolean phoneNumberDiscoverable = SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode().isDiscoverable();

    String deviceName = SignalStore.account().getDeviceName();
    byte[] encryptedDeviceName = (deviceName == null) ? null : DeviceNameCipher.encryptDeviceName(deviceName.getBytes(StandardCharsets.UTF_8), SignalStore.account().getAciIdentityKey());

    AccountAttributes.Capabilities capabilities = AppCapabilities.getCapabilities((svrValues.hasPin() && !svrValues.hasOptedOut()) || SignalStore.storageService().hasStorageKeyFromPrimary());
    Log.i(TAG, "Calling setAccountAttributes() reglockV2? " + !TextUtils.isEmpty(registrationLockV2) + ", pin? " + svrValues.hasPin() +
               "\n    Recovery password? " + !TextUtils.isEmpty(recoveryPassword) +
               "\n    Phone number discoverable : " + phoneNumberDiscoverable +
               "\n    Device Name : " + (encryptedDeviceName != null) +
               "\n  Capabilities: " + capabilities);

    AccountAttributes accountAttributes = new AccountAttributes(
        null,
        registrationId,
        fetchesMessages,
        registrationLockV2,
        unidentifiedAccessKey,
        universalUnidentifiedAccess,
        capabilities,
        phoneNumberDiscoverable,
        (encryptedDeviceName == null) ? null : Base64.encodeBytes(encryptedDeviceName),
        pniRegistrationId,
        recoveryPassword
    );

    ApplicationDependencies.getSignalServiceAccountManager().setAccountAttributes(accountAttributes);

    hasRefreshedThisAppCycle = true;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new RefreshAttributesJob(parameters, data.getBooleanOrDefault(KEY_FORCED, true));
    }
  }
}
