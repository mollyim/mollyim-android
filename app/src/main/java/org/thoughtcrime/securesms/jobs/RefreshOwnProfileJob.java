package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.SignalNetwork;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.NetworkResultUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;

import java.io.IOException;
import java.util.Objects;


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
  public @Nullable byte[] serialize() {
    return null;
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

    if ((SignalStore.svr().hasPin() || SignalStore.account().restoredAccountEntropyPool()) && !SignalStore.svr().hasOptedOut() && SignalStore.storageService().getLastSyncTime() == 0) {
      Log.i(TAG, "Registered with PIN or AEP but haven't completed storage sync yet.");
      return;
    }

    if (!SignalStore.registration().hasUploadedProfile() && SignalStore.account().isPrimaryDevice()) {
      Log.i(TAG, "Registered but haven't uploaded profile yet.");
      return;
    }

    Recipient self = Recipient.self();

    ProfileAndCredential profileAndCredential;
    try {
      profileAndCredential = ProfileUtil.retrieveProfileSync(context, self, getRequestType(self), false);
    } catch (IllegalStateException e) {
      Log.w(TAG, "Unexpected exception result from profile fetch. Skipping.");
      return;
    }


    SignalServiceProfile profile              = profileAndCredential.getProfile();

    if (Util.isEmpty(profile.getName()) &&
        Util.isEmpty(profile.getAvatar()) &&
        Util.isEmpty(profile.getAbout()) &&
        Util.isEmpty(profile.getAboutEmoji()))
    {
      Log.w(TAG, "The profile we retrieved was empty! Ignoring it.");

      if (!self.getProfileName().isEmpty()) {
        Log.w(TAG, "We have a name locally. Scheduling a profile upload.");
        AppDependencies.getJobManager().add(new ProfileUploadJob());
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
    ensurePhoneNumberSharingIsCorrect(profile.getPhoneNumberSharing());

    profileAndCredential.getExpiringProfileKeyCredential()
                        .ifPresent(expiringProfileKeyCredential -> setExpiringProfileKeyCredential(self, ProfileKeyUtil.getSelfProfileKey(), expiringProfileKeyCredential));

    SignalStore.registration().setHasDownloadedProfile(true);

    StoryOnboardingDownloadJob.Companion.enqueueIfNeeded();

    checkUsernameIsInSync();
  }

  private void setExpiringProfileKeyCredential(@NonNull Recipient recipient,
                                               @NonNull ProfileKey recipientProfileKey,
                                               @NonNull ExpiringProfileKeyCredential credential)
  {
    RecipientTable recipientTable = SignalDatabase.recipients();
    recipientTable.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
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
    AppDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), avatar));
  }

  private void setProfileCapabilities(@Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    Recipient selfSnapshot = Recipient.self();

    SignalDatabase.recipients().setCapabilities(Recipient.self().getId(), capabilities);
  }

  private void ensureUnidentifiedAccessCorrect(@Nullable String unidentifiedAccessVerifier, boolean universalUnidentifiedAccess) {
    if (unidentifiedAccessVerifier == null) {
      Log.w(TAG, "No unidentified access is set remotely! Refreshing attributes.");
      AppDependencies.getJobManager().add(new RefreshAttributesJob());
      return;
    }

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context) != universalUnidentifiedAccess) {
      Log.w(TAG, "The universal access flag doesn't match our local value (local: " + TextSecurePreferences.isUniversalUnidentifiedAccess(context) + ", remote: " + universalUnidentifiedAccess + ")! Refreshing attributes.");
      AppDependencies.getJobManager().add(new RefreshAttributesJob());
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
      AppDependencies.getJobManager().add(new RefreshAttributesJob());
    }
  }

  /**
   * Checks to make sure that our phone number sharing setting matches what's on our profile. If there's a mismatch, we first sync with storage service
   * (to limit race conditions between devices) and then upload our profile.
   */
  private void ensurePhoneNumberSharingIsCorrect(@Nullable String phoneNumberSharingCiphertext) {
    if (phoneNumberSharingCiphertext == null) {
      Log.w(TAG, "No phone number sharing is set remotely! Syncing with storage service, then uploading our profile.");
      syncWithStorageServiceThenUploadProfile();
      return;
    }

    ProfileKey    profileKey = ProfileKeyUtil.getSelfProfileKey();
    ProfileCipher cipher     = new ProfileCipher(profileKey);

    try {
      RecipientTable.PhoneNumberSharingState remotePhoneNumberSharing = cipher.decryptBoolean(Base64.decode(phoneNumberSharingCiphertext))
                                                                              .map(value -> value ? RecipientTable.PhoneNumberSharingState.ENABLED : RecipientTable.PhoneNumberSharingState.DISABLED)
                                                                              .orElse(RecipientTable.PhoneNumberSharingState.UNKNOWN);

      if (remotePhoneNumberSharing == RecipientTable.PhoneNumberSharingState.UNKNOWN || remotePhoneNumberSharing.getEnabled() != SignalStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()) {
        Log.w(TAG, "Phone number sharing setting did not match! Syncing with storage service, then uploading our profile.");
        syncWithStorageServiceThenUploadProfile();
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode phone number sharing! Syncing with storage service, then uploading our profile.", e);
      syncWithStorageServiceThenUploadProfile();
    } catch (InvalidCiphertextException e) {
      Log.w(TAG, "Failed to decrypt phone number sharing! Syncing with storage service, then uploading our profile.", e);
      syncWithStorageServiceThenUploadProfile();
    }
  }

  private void syncWithStorageServiceThenUploadProfile() {
    AppDependencies.getJobManager()
                   .startChain(StorageSyncJob.forRemoteChange())
                   .then(new ProfileUploadJob())
                   .enqueue();
  }

  private static void checkUsernameIsInSync() {
    boolean validated = false;

    try {
      String localUsername = SignalStore.account().getUsername();

      WhoAmIResponse whoAmIResponse     = AppDependencies.getSignalServiceAccountManager().getWhoAmI();
      String         remoteUsernameHash = whoAmIResponse.getUsernameHash();
      String         localUsernameHash  = localUsername != null ? Base64.encodeUrlSafeWithoutPadding(new Username(localUsername).getHash()) : null;

      if (TextUtils.isEmpty(localUsernameHash) && TextUtils.isEmpty(remoteUsernameHash)) {
        Log.d(TAG, "Local and remote username hash are both empty. Considering validated.");
        UsernameRepository.onUsernameConsistencyValidated();
      } else if (!Objects.equals(localUsernameHash, remoteUsernameHash)) {
        Log.w(TAG, "Local username hash does not match server username hash. Local hash: " + (TextUtils.isEmpty(localUsername) ? "empty" : "present") + ", Remote hash: " + (TextUtils.isEmpty(remoteUsernameHash) ? "empty" : "present"));
        UsernameRepository.onUsernameMismatchDetected();
        return;
      } else {
        Log.d(TAG, "Username validated.");
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed perform synchronization check during username phase.", e);
    } catch (BaseUsernameException e) {
      Log.w(TAG, "Our local username data is invalid!", e);
      UsernameRepository.onUsernameMismatchDetected();
      return;
    }

    try {
      UsernameLinkComponents localUsernameLink = SignalStore.account().getUsernameLink();

      if (localUsernameLink != null) {
        byte[]                remoteEncryptedUsername = NetworkResultUtil.toBasicLegacy(SignalNetwork.username().getEncryptedUsernameFromLinkServerId(localUsernameLink.getServerId()));
        Username.UsernameLink combinedLink            = new Username.UsernameLink(localUsernameLink.getEntropy(), remoteEncryptedUsername);
        Username              remoteUsername          = Username.fromLink(combinedLink);

        if (!remoteUsername.getUsername().equals(SignalStore.account().getUsername())) {
          Log.w(TAG, "The remote username decrypted ok, but the decrypted username did not match our local username!");
          UsernameRepository.onUsernameLinkMismatchDetected();
        } else {
          Log.d(TAG, "Username link validated.");
        }

        validated = true;
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed perform synchronization check during the username link phase.", e);
    } catch (BaseUsernameException e) {
      Log.w(TAG, "Failed to decrypt username link using the remote encrypted username and our local entropy!", e);
      UsernameRepository.onUsernameLinkMismatchDetected();
    }

    if (validated) {
      UsernameRepository.onUsernameConsistencyValidated();
    }
  }

  public static final class Factory implements Job.Factory<RefreshOwnProfileJob> {

    @Override
    public @NonNull RefreshOwnProfileJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new RefreshOwnProfileJob(parameters);
    }
  }
}
