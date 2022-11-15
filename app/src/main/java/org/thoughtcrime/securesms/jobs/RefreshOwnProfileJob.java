package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;

import java.io.IOException;

/**
 * Refreshes the profile of the local user. Different from {@link RetrieveProfileJob} in that we
 * have to sometimes look at/set different data stores, and we will *always* do the fetch regardless
 * of caching.
 */
public class RefreshOwnProfileJob extends BaseJob {

  public static final String KEY = "RefreshOwnProfileJob";

  private static final String TAG = Log.tag(RefreshOwnProfileJob.class);

  public RefreshOwnProfileJob() {
    this(ProfileUploadJob.QUEUE);
  }

  private RefreshOwnProfileJob(@NonNull String queue) {
    this(new Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue(queue)
             .setMaxInstancesForFactory(1)
             .setMaxAttempts(10)
             .build());
  }

  private RefreshOwnProfileJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!SignalStore.account().isRegistered() || TextUtils.isEmpty(SignalStore.account().getE164())) {
      Log.w(TAG, "Not yet registered!");
      return;
    }

    if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut() && SignalStore.storageService().getLastSyncTime() == 0) {
      Log.i(TAG, "Registered with PIN but haven't completed storage sync yet.");
      return;
    }

    if (!SignalStore.registrationValues().hasUploadedProfile() && SignalStore.account().isPrimaryDevice()) {
      Log.i(TAG, "Registered but haven't uploaded profile yet.");
      return;
    }

    Recipient            self                 = Recipient.self();
    ProfileAndCredential profileAndCredential = ProfileUtil.retrieveProfileSync(context, self, getRequestType(self), false);
    SignalServiceProfile profile              = profileAndCredential.getProfile();

    if (Util.isEmpty(profile.getName()) &&
        Util.isEmpty(profile.getAvatar()) &&
        Util.isEmpty(profile.getAbout()) &&
        Util.isEmpty(profile.getAboutEmoji()))
    {
      Log.w(TAG, "The profile we retrieved was empty! Ignoring it.");

      if (!self.getProfileName().isEmpty()) {
        Log.w(TAG, "We have a name locally. Scheduling a profile upload.");
        ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      } else {
        Log.w(TAG, "We don't have a name locally, either!");
      }

      return;
    }

    setProfileName(profile.getName());
    setProfileAbout(profile.getAbout(), profile.getAboutEmoji());
    setProfileAvatar(profile.getAvatar());
    setProfileCapabilities(profile.getCapabilities());
    ensureUnidentifiedAccessCorrect(profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    profileAndCredential.getExpiringProfileKeyCredential()
                        .ifPresent(expiringProfileKeyCredential -> setExpiringProfileKeyCredential(self, ProfileKeyUtil.getSelfProfileKey(), expiringProfileKeyCredential));

    String username = ApplicationDependencies.getSignalServiceAccountManager().getWhoAmI().getUsername();
    SignalDatabase.recipients().setUsername(Recipient.self().getId(), username);

    StoryOnboardingDownloadJob.Companion.enqueueIfNeeded();
  }

  private void setExpiringProfileKeyCredential(@NonNull Recipient recipient,
                                               @NonNull ProfileKey recipientProfileKey,
                                               @NonNull ExpiringProfileKeyCredential credential)
  {
    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    recipientDatabase.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential()) ? SignalServiceProfile.RequestType.PROFILE
                                                                                              : SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() { }

  private void setProfileName(@Nullable String encryptedName) {
    try {
      ProfileKey  profileKey    = ProfileKeyUtil.getSelfProfileKey();
      String      plaintextName = ProfileUtil.decryptString(profileKey, encryptedName);
      ProfileName profileName   = ProfileName.fromSerialized(plaintextName);

      if (!profileName.isEmpty()) {
        Log.d(TAG, "Saving non-empty name.");
        SignalDatabase.recipients().setProfileName(Recipient.self().getId(), profileName);
      } else {
        Log.w(TAG, "Ignoring empty name.");
      }

    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setProfileAbout(@Nullable String encryptedAbout, @Nullable String encryptedEmoji) {
    try {
      ProfileKey  profileKey     = ProfileKeyUtil.getSelfProfileKey();
      String      plaintextAbout = ProfileUtil.decryptString(profileKey, encryptedAbout);
      String      plaintextEmoji = ProfileUtil.decryptString(profileKey, encryptedEmoji);

      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextAbout) ? "non-" : "") + "empty about.");
      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextEmoji) ? "non-" : "") + "empty emoji.");

      SignalDatabase.recipients().setAbout(Recipient.self().getId(), plaintextAbout, plaintextEmoji);
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private static void setProfileAvatar(@Nullable String avatar) {
    Log.d(TAG, "Saving " + (!Util.isEmpty(avatar) ? "non-" : "") + "empty avatar.");
    ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), avatar));
  }

  private void setProfileCapabilities(@Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    SignalDatabase.recipients().setCapabilities(Recipient.self().getId(), capabilities);
  }

  private void ensureUnidentifiedAccessCorrect(@Nullable String unidentifiedAccessVerifier, boolean universalUnidentifiedAccess) {
    if (unidentifiedAccessVerifier == null) {
      Log.w(TAG, "No unidentified access is set remotely! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return;
    }

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context) != universalUnidentifiedAccess) {
      Log.w(TAG, "The universal access flag doesn't match our local value (local: " + TextSecurePreferences.isUniversalUnidentifiedAccess(context) + ", remote: " + universalUnidentifiedAccess + ")! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return;
    }

    ProfileKey    profileKey = ProfileKeyUtil.getSelfProfileKey();
    ProfileCipher cipher     = new ProfileCipher(profileKey);

    boolean verified;
    try {
      verified = cipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode unidentified access!", e);
      verified = false;
    }

    if (!verified) {
      Log.w(TAG, "Unidentified access failed to verify! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }
  }

  public static final class Factory implements Job.Factory<RefreshOwnProfileJob> {

    @Override
    public @NonNull RefreshOwnProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RefreshOwnProfileJob(parameters);
    }
  }
}
