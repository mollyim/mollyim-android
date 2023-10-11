/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.signal.libsignal.usernames.Username.UsernameLink;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest;
import org.whispersystems.signalservice.api.account.PniKeyDistributionRequest;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.VerifyDeviceResponse;
import org.whispersystems.signalservice.api.payments.CurrencyConversions;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.services.CdsiV2Service;
import org.whispersystems.signalservice.api.storage.SignalStorageCipher;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageModels;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.storage.StorageManifestKey;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.BackupAuthCheckRequest;
import org.whispersystems.signalservice.internal.push.BackupAuthCheckResponse;
import org.whispersystems.signalservice.internal.push.CdsiAuthResponse;
import org.whispersystems.signalservice.internal.push.OneTimePreKeyCounts;
import org.whispersystems.signalservice.internal.push.ConfirmCodeMessage;
import org.whispersystems.signalservice.internal.push.PaymentAddress;
import org.whispersystems.signalservice.internal.push.ProfileAvatarData;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisioningSocket;
import org.whispersystems.signalservice.internal.push.ProvisioningVersion;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageItems;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Single;
import okio.ByteString;

/**
 * The main interface for creating, registering, and
 * managing a Signal Service account.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAccountManager {

  private static final String TAG = SignalServiceAccountManager.class.getSimpleName();

  private static final int STORAGE_READ_MAX_ITEMS = 1000;

  private final PushServiceSocket          pushServiceSocket;
  private final ProvisioningSocket         provisioningSocket;
  private final CredentialsProvider        credentials;
  private final String                     userAgent;
  private final GroupsV2Operations         groupsV2Operations;
  private final SignalServiceConfiguration configuration;


  /**
   * Construct a SignalServiceAccountManager.
   * @param configuration The URL for the Signal Service.
   * @param aci The Signal Service ACI.
   * @param pni The Signal Service PNI.
   * @param e164 The Signal Service phone number.
   * @param password A Signal Service password.
   * @param signalAgent A string which identifies the client software.
   */
  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     ACI aci,
                                     PNI pni,
                                     String e164,
                                     int deviceId,
                                     String password,
                                     String signalAgent,
                                     boolean automaticNetworkRetry,
                                     int maxGroupSize)
  {
    this(configuration,
         new StaticCredentialsProvider(aci, pni, e164, deviceId, password),
         signalAgent,
         new GroupsV2Operations(ClientZkOperations.create(configuration), maxGroupSize),
         automaticNetworkRetry);
  }

  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     CredentialsProvider credentialsProvider,
                                     String signalAgent,
                                     GroupsV2Operations groupsV2Operations,
                                     boolean automaticNetworkRetry)
  {
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = new PushServiceSocket(configuration, credentialsProvider, signalAgent, groupsV2Operations == null ? null : groupsV2Operations.getProfileOperations(), automaticNetworkRetry);
    this.provisioningSocket = new ProvisioningSocket(configuration, signalAgent);
    this.credentials        = credentialsProvider;
    this.userAgent          = signalAgent;
    this.configuration      = configuration;
  }

  public byte[] getSenderCertificate() throws IOException {
    return this.pushServiceSocket.getSenderCertificate();
  }

  public byte[] getSenderCertificateForPhoneNumberPrivacy() throws IOException {
    return this.pushServiceSocket.getUuidOnlySenderCertificate();
  }

  public SecureValueRecoveryV2 getSecureValueRecoveryV2(String mrEnclave) {
    return new SecureValueRecoveryV2(configuration, mrEnclave, pushServiceSocket);
  }

  /**
   * V1 PINs are no longer used in favor of V2 PINs stored on KBS.
   *
   * You can remove a V1 PIN, but typically this is unnecessary, as setting a V2 PIN via
   * {@link KeyBackupService.PinChangeSession#enableRegistrationLock(MasterKey)}} will automatically clear the
   * V1 PIN on the service.
   */
  public void removeRegistrationLockV1() throws IOException {
    this.pushServiceSocket.removeRegistrationLockV1();
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return this.pushServiceSocket.getWhoAmI();
  }

  public KeyBackupService getKeyBackupService(KeyStore iasKeyStore,
                                              String enclaveName,
                                              byte[] serviceId,
                                              String mrenclave,
                                              int tries)
  {
    return new KeyBackupService(iasKeyStore, enclaveName, serviceId, mrenclave, pushServiceSocket, tries);
  }

  /**
   * Register/Unregister a Google Cloud Messaging registration ID.
   *
   * @param gcmRegistrationId The GCM id to register.  A call with an absent value will unregister.
   * @throws IOException
   */
  public void setGcmId(Optional<String> gcmRegistrationId) throws IOException {
    if (gcmRegistrationId.isPresent()) {
      this.pushServiceSocket.registerGcmId(gcmRegistrationId.get());
    } else {
      this.pushServiceSocket.unregisterGcmId();
    }
  }

  public Single<ServiceResponse<BackupAuthCheckResponse>> checkBackupAuthCredentials(@Nonnull String e164, @Nonnull List<String> usernamePasswords) {

    return pushServiceSocket.checkBackupAuthCredentials(new BackupAuthCheckRequest(e164, usernamePasswords), DefaultResponseMapper.getDefault(BackupAuthCheckResponse.class));
  }

  /**
   * Request a push challenge. A number will be pushed to the GCM (FCM) id. This can then be used
   * during SMS/call requests to bypass the CAPTCHA.
   *
   * @param gcmRegistrationId The GCM (FCM) id to use.
   * @param sessionId         The session to request a push for.
   * @throws IOException
   */
  public void requestRegistrationPushChallenge(String sessionId, String gcmRegistrationId) throws IOException {
    pushServiceSocket.requestPushChallenge(sessionId, gcmRegistrationId);
  }

  public ServiceResponse<RegistrationSessionMetadataResponse> createRegistrationSession(@Nullable String fcmToken, @Nullable String mcc, @Nullable String mnc) {
    try {
      final RegistrationSessionMetadataResponse response =  pushServiceSocket.createVerificationSession(fcmToken, mcc, mnc);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public ServiceResponse<RegistrationSessionMetadataResponse> getRegistrationSession(String sessionId) {
    try {
      final RegistrationSessionMetadataResponse response = pushServiceSocket.getSessionStatus(sessionId);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public ServiceResponse<RegistrationSessionMetadataResponse> submitPushChallengeToken(String sessionId, String pushChallengeToken) {
    try {
      final RegistrationSessionMetadataResponse response = pushServiceSocket.patchVerificationSession(sessionId, null, null, null, null, pushChallengeToken);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public ServiceResponse<RegistrationSessionMetadataResponse> submitCaptchaToken(String sessionId, @Nullable String captchaToken) {
    try {
      final RegistrationSessionMetadataResponse response = pushServiceSocket.patchVerificationSession(sessionId, null, null, null, captchaToken, null);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this Signal user.
   *
   * @param androidSmsRetrieverSupported
   */
  public ServiceResponse<RegistrationSessionMetadataResponse> requestSmsVerificationCode(String sessionId, Locale locale, boolean androidSmsRetrieverSupported) {
    try {
      final RegistrationSessionMetadataResponse response = pushServiceSocket.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, PushServiceSocket.VerificationCodeTransport.SMS);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Request a Voice verification code.  On success, the server will
   * make a voice call to this Signal user.
   *
   * @param locale
   */
  public ServiceResponse<RegistrationSessionMetadataResponse> requestVoiceVerificationCode(String sessionId, Locale locale, boolean androidSmsRetrieverSupported) {
    try {
      final RegistrationSessionMetadataResponse response = pushServiceSocket.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, PushServiceSocket.VerificationCodeTransport.VOICE);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Verify a Signal Service account with a received SMS or voice verification code.
   *
   * @param verificationCode The verification code received via SMS or Voice
   *                         (see {@link #requestSmsVerificationCode} and
   *                         {@link #requestVoiceVerificationCode}).
   * @param sessionId        The ID of the current registration session.
   * @return The UUID of the user that was registered.
   * @throws IOException for various HTTP and networking errors
   */
  public ServiceResponse<RegistrationSessionMetadataResponse> verifyAccount(@Nonnull String verificationCode, @Nonnull String sessionId) {
    try {
      RegistrationSessionMetadataResponse response = pushServiceSocket.submitVerificationCode(sessionId, verificationCode);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public @Nonnull ServiceResponse<VerifyAccountResponse> registerAccount(@Nullable String sessionId, @Nullable String recoveryPassword, AccountAttributes attributes, PreKeyCollection aciPreKeys, PreKeyCollection pniPreKeys, String fcmToken, boolean skipDeviceTransfer) {
    try {
      VerifyAccountResponse response = pushServiceSocket.submitRegistrationRequest(sessionId, recoveryPassword, attributes, aciPreKeys, pniPreKeys, fcmToken, skipDeviceTransfer);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public @Nonnull ServiceResponse<VerifyAccountResponse> changeNumber(@Nonnull ChangePhoneNumberRequest changePhoneNumberRequest) {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.changeNumber(changePhoneNumberRequest);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Refresh account attributes with server.
   *
   * @throws IOException
   */
  public void setAccountAttributes(@Nonnull AccountAttributes accountAttributes)
      throws IOException
  {
    this.pushServiceSocket.setAccountAttributes(accountAttributes);
  }

  /**
   * Register an identity key, signed prekey, and list of one time prekeys
   * with the server.
   *
   * @throws IOException
   */
  public void setPreKeys(PreKeyUpload preKeyUpload)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(preKeyUpload);
  }

  /**
   * @return The server's count of currently available (eg. unused) prekeys for this user.
   * @throws IOException
   */
  public OneTimePreKeyCounts getPreKeyCounts(ServiceIdType serviceIdType) throws IOException {
    return this.pushServiceSocket.getAvailablePreKeys(serviceIdType);
  }

  /**
   * Set the client's signed prekey.
   *
   * @param signedPreKey The client's new signed prekey.
   * @throws IOException
   */
  public void setSignedPreKey(ServiceIdType serviceIdType, SignedPreKeyRecord signedPreKey) throws IOException {
    this.pushServiceSocket.setCurrentSignedPreKey(serviceIdType, signedPreKey);
  }

  /**
   * @return True if the identifier corresponds to a registered user, otherwise false.
   */
  public boolean isIdentifierRegistered(ServiceId identifier) throws IOException {
    return pushServiceSocket.isIdentifierRegistered(identifier);
  }

  /**
   * Request a UUID from the server for linking as a new device.
   * Called by the new device.
   * @return The UUID, Base64 encoded
   * @throws TimeoutException
   * @throws IOException
   */
  public String getNewDeviceUuid() throws TimeoutException, IOException {
    return provisioningSocket.getProvisioningUuid().uuid;
  }

  @SuppressWarnings("SameParameterValue")
  public CdsiV2Service.Response getRegisteredUsersWithCdsi(Set<String> previousE164s,
                                                           Set<String> newE164s,
                                                           Map<ServiceId, ProfileKey> serviceIds,
                                                           boolean requireAcis,
                                                           Optional<byte[]> token,
                                                           String mrEnclave,
                                                           Long timeoutMs,
                                                           Consumer<byte[]> tokenSaver)
      throws IOException
  {
    CdsiAuthResponse                                auth    = pushServiceSocket.getCdsiAuth();
    CdsiV2Service                                   service = new CdsiV2Service(configuration, mrEnclave);
    CdsiV2Service.Request                           request = new CdsiV2Service.Request(previousE164s, newE164s, serviceIds, requireAcis, token);
    Single<ServiceResponse<CdsiV2Service.Response>> single  = service.getRegisteredUsers(auth.getUsername(), auth.getPassword(), request, tokenSaver);

    ServiceResponse<CdsiV2Service.Response> serviceResponse;
    try {
      if (timeoutMs == null) {
        serviceResponse = single
            .blockingGet();
      } else {
        serviceResponse = single
            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
            .blockingGet();
      }
    } catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InterruptedException) {
        throw new IOException("Interrupted", cause);
      } else if (cause instanceof TimeoutException) {
        throw new IOException("Timed out");
      } else {
        throw e;
      }
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception when retrieving registered users!", e);
    }

    if (serviceResponse.getResult().isPresent()) {
      return serviceResponse.getResult().get();
    } else if (serviceResponse.getApplicationError().isPresent()) {
      if (serviceResponse.getApplicationError().get() instanceof IOException) {
        throw (IOException) serviceResponse.getApplicationError().get();
      } else {
        throw new IOException(serviceResponse.getApplicationError().get());
      }
    } else if (serviceResponse.getExecutionError().isPresent()) {
      throw new IOException(serviceResponse.getExecutionError().get());
    } else {
      throw new IOException("Missing result!");
    }
  }


  public Optional<SignalStorageManifest> getStorageManifest(StorageKey storageKey) throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (InvalidKeyException | NotFoundException e) {
      Log.w(TAG, "Error while fetching manifest.", e);
      return Optional.empty();
    }
  }

  public long getStorageManifestVersion() throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return  storageManifest.version;
    } catch (NotFoundException e) {
      return 0;
    }
  }

  public Optional<SignalStorageManifest> getStorageManifestIfDifferentVersion(StorageKey storageKey, long manifestVersion) throws IOException, InvalidKeyException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifestIfDifferentVersion(authToken, manifestVersion);

      if (storageManifest.value_.size() == 0) {
        Log.w(TAG, "Got an empty storage manifest!");
        return Optional.empty();
      }

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (NoContentException e) {
      return Optional.empty();
    }
  }

  public List<SignalStorageRecord> readStorageRecords(StorageKey storageKey, List<StorageId> storageKeys) throws IOException, InvalidKeyException {
    if (storageKeys.isEmpty()) {
      return Collections.emptyList();
    }

    List<SignalStorageRecord> result         = new ArrayList<>();
    Map<ByteString, Integer>  typeMap        = new HashMap<>();
    List<ReadOperation>       readOperations = new LinkedList<>();
    List<ByteString>          readKeys       = new LinkedList<>();

    for (StorageId key : storageKeys) {
      typeMap.put(ByteString.of(key.getRaw()), key.getType());

      if (readKeys.size() >= STORAGE_READ_MAX_ITEMS) {
        Log.i(TAG, "Going over max read items. Starting a new read operation.");
        readOperations.add(new ReadOperation.Builder().readKey(readKeys).build());
        readKeys = new LinkedList<>();
      }

      if (StorageId.isKnownType(key.getType())) {
        readKeys.add(ByteString.of(key.getRaw()));
      } else {
        result.add(SignalStorageRecord.forUnknown(key));
      }
    }

    if (readKeys.size() > 0) {
      readOperations.add(new ReadOperation.Builder().readKey(readKeys).build());
    }

    Log.i(TAG, "Reading " + storageKeys.size() + " items split over " + readOperations.size() + " page(s).");

    String authToken = this.pushServiceSocket.getStorageAuth();

    for (ReadOperation readOperation : readOperations) {
      StorageItems items = this.pushServiceSocket.readStorageItems(authToken, readOperation);

      for (StorageItem item : items.items) {
        Integer type = typeMap.get(item.key);
        if (type != null) {
          result.add(SignalStorageModels.remoteToLocalStorageRecord(item, type, storageKey));
        } else {
          Log.w(TAG, "No type found! Skipping.");
        }
      }
    }

    return result;
  }
  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> resetStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> allRecords)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, allRecords, Collections.<byte[]>emptyList(), true);
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> inserts,
                                                             List<byte[]> deletes)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, inserts, deletes, false);
  }


  /**
   * Enables registration lock for this account.
   */
  public void enableRegistrationLock(MasterKey masterKey) throws IOException {
    pushServiceSocket.setRegistrationLockV2(masterKey.deriveRegistrationLock());
  }

  /**
   * Disables registration lock for this account.
   */
  public void disableRegistrationLock() throws IOException {
    pushServiceSocket.disableRegistrationLockV2();
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  private Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                              SignalStorageManifest manifest,
                                                              List<SignalStorageRecord> inserts,
                                                              List<byte[]> deletes,
                                                              boolean clearAll)
      throws IOException, InvalidKeyException
  {
    ManifestRecord.Builder manifestRecordBuilder = new ManifestRecord.Builder()
                                                                     .sourceDevice(manifest.getSourceDeviceId())
                                                                     .version(manifest.getVersion());


    manifestRecordBuilder.identifiers(
        manifest.getStorageIds().stream()
                .map(id -> new ManifestRecord.Identifier.Builder()
                                                        .raw(ByteString.of(id.getRaw()))
                                                        .type(ManifestRecord.Identifier.Type.Companion.fromValue(id.getType()))
                                                        .build())
                .collect(Collectors.toList())
    );

    String             authToken       = this.pushServiceSocket.getStorageAuth();
    StorageManifestKey manifestKey     = storageKey.deriveManifestKey(manifest.getVersion());
    byte[]             encryptedRecord = SignalStorageCipher.encrypt(manifestKey, manifestRecordBuilder.build().encode());
    StorageManifest    storageManifest = new StorageManifest.Builder()
                                                            .version(manifest.getVersion())
                                                            .value_(ByteString.of(encryptedRecord))
                                                            .build();

    WriteOperation.Builder writeBuilder = new WriteOperation.Builder().manifest(storageManifest);

    writeBuilder.insertItem(
        inserts.stream()
               .map(insert -> SignalStorageModels.localToRemoteStorageRecord(insert, storageKey))
               .collect(Collectors.toList())
    );

    if (clearAll) {
      writeBuilder.clearAll(true);
    } else {
      writeBuilder.deleteKey(
          deletes.stream()
                 .map(delete -> ByteString.of(delete))
                 .collect(Collectors.toList())
      );
    }

    Optional<StorageManifest> conflict = this.pushServiceSocket.writeStorageContacts(authToken, writeBuilder.build());

    if (conflict.isPresent()) {
      StorageManifestKey conflictKey       = storageKey.deriveManifestKey(conflict.get().version);
      byte[]             rawManifestRecord = SignalStorageCipher.decrypt(conflictKey, conflict.get().value_.toByteArray());
      ManifestRecord     record            = ManifestRecord.ADAPTER.decode(rawManifestRecord);
      List<StorageId>    ids               = new ArrayList<>(record.identifiers.size());

      for (ManifestRecord.Identifier id : record.identifiers) {
        ids.add(StorageId.forType(id.raw.toByteArray(), id.type.getValue()));
      }

      SignalStorageManifest conflictManifest = new SignalStorageManifest(record.version, record.sourceDevice, ids);

      return Optional.of(conflictManifest);
    } else {
      return Optional.empty();
    }
  }

  public RemoteConfigResult getRemoteConfig() throws IOException {
    RemoteConfigResponse response = this.pushServiceSocket.getRemoteConfig();
    Map<String, Object>  out      = new HashMap<>();

    for (RemoteConfigResponse.Config config : response.getConfig()) {
      out.put(config.getName(), config.getValue() != null ? config.getValue() : config.isEnabled());
    }

    return new RemoteConfigResult(out, response.getServerEpochTime());
  }

  public String getAccountDataReport() throws IOException {
    return pushServiceSocket.getAccountDataReport();
  }

  public String getNewDeviceVerificationCode() throws IOException {
    return this.pushServiceSocket.getNewDeviceVerificationCode();
  }

  /**
   * Gets info from the primary device to finish the registration as a new device.<br>
   * @param tempIdentity A temporary identity. Must be the same as the one given to the already verified device.
   * @return Contains the account's permanent IdentityKeyPair and it's number along with the provisioning code required to finish the registration.
   */
  public NewDeviceRegistrationReturn getNewDeviceRegistration(IdentityKeyPair tempIdentity) throws TimeoutException, IOException {
    ProvisionMessage msg = provisioningSocket.getProvisioningMessage(tempIdentity);

    final String number = msg.number;
    final ACI    aci    = ACI.parseOrNull(msg.aci);
    final PNI    pni    = PNI.parseOrNull(msg.pni);

    if (credentials instanceof StaticCredentialsProvider) {
      // Not setting ACI or PNI here, as that causes a 400 Bad Request
      // when calling the finishNewDeviceRegistration endpoint.
      ((StaticCredentialsProvider) credentials).setE164(number);
    }

    final IdentityKeyPair aciIdentity = getIdentityKeyPair(msg.aciIdentityKeyPublic.toByteArray(), msg.aciIdentityKeyPrivate.toByteArray());
    final IdentityKeyPair pniIdentity = msg.pniIdentityKeyPublic != null && msg.pniIdentityKeyPrivate != null
                                        ? getIdentityKeyPair(msg.pniIdentityKeyPublic.toByteArray(), msg.pniIdentityKeyPrivate.toByteArray())
                                        : null;

    final ProfileKey profileKey;
    try {
      profileKey = msg.profileKey != null ? new ProfileKey(msg.profileKey.toByteArray()) : null;
    } catch (InvalidInputException e) {
      throw new IOException("Failed to decrypt profile key", e);
    }

    final String  provisioningCode = msg.provisioningCode;
    final boolean readReceipts     = msg.readReceipts != null && msg.readReceipts;

    return new NewDeviceRegistrationReturn(
        provisioningCode,
        aciIdentity, pniIdentity,
        number, aci, pni,
        profileKey,
        readReceipts
    );
  }

  private IdentityKeyPair getIdentityKeyPair(byte[] publicKeyBytes, byte[] privateKeyBytes) throws IOException {
    if (publicKeyBytes.length == 32) {
      // The public key is missing the type specifier, probably from iOS
      // Signal-Desktop handles this by ignoring the sent public key and regenerating it from the private key
      byte[] type = {Curve.DJB_TYPE};
      publicKeyBytes = ByteUtil.combine(type, publicKeyBytes);
    }
    final ECPublicKey publicKey;
    final ECPrivateKey    privateKey;
    try {
      publicKey = Curve.decodePoint(publicKeyBytes, 0);
      privateKey = Curve.decodePrivatePoint(privateKeyBytes);
    } catch (InvalidKeyException e) {
      throw new IOException("Failed to decrypt key", e);
    }
    return new IdentityKeyPair(new IdentityKey(publicKey), privateKey);
  }

  /**
   * Finishes a registration as a new device. Called by the new device.<br>
   * This method blocks until the already verified device has verified this device.
   * @param provisioningCode The provisioning code from the getNewDeviceRegistration method
   * @return The deviceId given by the server.
   */
  public int finishNewDeviceRegistration(String provisioningCode, ConfirmCodeMessage confirmCodeMessage) throws IOException {
    int deviceId = this.pushServiceSocket.finishNewDeviceRegistration(provisioningCode, confirmCodeMessage);
    if (credentials instanceof StaticCredentialsProvider) {
      ((StaticCredentialsProvider) credentials).setDeviceId(deviceId);
    }
    return deviceId;
  }

  public void addDevice(String deviceIdentifier,
                        ECPublicKey deviceKey,
                        IdentityKeyPair aciIdentityKeyPair,
                        IdentityKeyPair pniIdentityKeyPair,
                        ProfileKey profileKey,
                        String code)
      throws InvalidKeyException, IOException
  {
    String e164 = credentials.getE164();
    ACI    aci  = credentials.getAci();
    PNI    pni  = credentials.getPni();

    Preconditions.checkArgument(e164 != null, "Missing e164!");
    Preconditions.checkArgument(aci != null, "Missing ACI!");
    Preconditions.checkArgument(pni != null, "Missing PNI!");

    PrimaryProvisioningCipher cipher  = new PrimaryProvisioningCipher(deviceKey);
    ProvisionMessage.Builder  message = new ProvisionMessage.Builder()
                                                            .aciIdentityKeyPublic(ByteString.of(aciIdentityKeyPair.getPublicKey().serialize()))
                                                            .aciIdentityKeyPrivate(ByteString.of(aciIdentityKeyPair.getPrivateKey().serialize()))
                                                            .pniIdentityKeyPublic(ByteString.of(pniIdentityKeyPair.getPublicKey().serialize()))
                                                            .pniIdentityKeyPrivate(ByteString.of(pniIdentityKeyPair.getPrivateKey().serialize()))
                                                            .aci(aci.toString())
                                                            .pni(pni.toStringWithoutPrefix())
                                                            .number(e164)
                                                            .profileKey(ByteString.of(profileKey.serialize()))
                                                            .provisioningCode(code)
                                                            .provisioningVersion(ProvisioningVersion.CURRENT.getValue());

    byte[] ciphertext = cipher.encrypt(message.build());
    this.pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext);
  }

  public ServiceResponse<VerifyAccountResponse> distributePniKeys(PniKeyDistributionRequest request) {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.distributePniKeys(request);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public List<DeviceInfo> getDevices() throws IOException {
    return this.pushServiceSocket.getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    this.pushServiceSocket.removeDevice(deviceId);
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    return this.pushServiceSocket.getTurnServerInfo();
  }

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
  }

  public CurrencyConversions getCurrencyConversions() throws IOException {
    return this.pushServiceSocket.getCurrencyConversions();
  }

  public void reportSpam(ServiceId serviceId, String serverGuid, String reportingToken) throws IOException {
    this.pushServiceSocket.reportSpam(serviceId, serverGuid, reportingToken);
  }

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> setVersionedProfile(ACI aci,
                                              ProfileKey profileKey,
                                              String name,
                                              String about,
                                              String aboutEmoji,
                                              Optional<PaymentAddress> paymentsAddress,
                                              AvatarUploadParams avatar,
                                              List<String> visibleBadgeIds)
      throws IOException
  {
    if (name == null) name = "";

    ProfileCipher     profileCipher               = new ProfileCipher(profileKey);
    byte[]            ciphertextName              = profileCipher.encryptString(name, ProfileCipher.getTargetNameLength(name));
    byte[]            ciphertextAbout             = profileCipher.encryptString(about, ProfileCipher.getTargetAboutLength(about));
    byte[]            ciphertextEmoji             = profileCipher.encryptString(aboutEmoji, ProfileCipher.EMOJI_PADDED_LENGTH);
    byte[]            ciphertextMobileCoinAddress = paymentsAddress.map(address -> profileCipher.encryptWithLength(address.encode(), ProfileCipher.PAYMENTS_ADDRESS_CONTENT_SIZE)).orElse(null);
    ProfileAvatarData profileAvatarData           = null;

    if (avatar.stream != null && !avatar.keepTheSame) {
      profileAvatarData = new ProfileAvatarData(avatar.stream.getStream(),
                                                ProfileCipherOutputStream.getCiphertextLength(avatar.stream.getLength()),
                                                avatar.stream.getContentType(),
                                                new ProfileCipherOutputStreamFactory(profileKey));
    }

    return this.pushServiceSocket.writeProfile(new SignalServiceProfileWrite(profileKey.getProfileKeyVersion(aci.getLibSignalAci()).serialize(),
                                                                             ciphertextName,
                                                                             ciphertextAbout,
                                                                             ciphertextEmoji,
                                                                             ciphertextMobileCoinAddress,
                                                                             avatar.hasAvatar,
                                                                             avatar.keepTheSame,
                                                                             profileKey.getCommitment(aci.getLibSignalAci()).serialize(),
                                                                             visibleBadgeIds),
                                                                             profileAvatarData);
  }

  public Optional<ExpiringProfileKeyCredential> resolveProfileKeyCredential(ACI serviceId, ProfileKey profileKey, Locale locale)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ProfileAndCredential credential = this.pushServiceSocket.retrieveVersionedProfileAndCredential(serviceId, profileKey, Optional.empty(), locale).get(10, TimeUnit.SECONDS);
      return credential.getExpiringProfileKeyCredential();
    } catch (InterruptedException | TimeoutException e) {
      throw new PushNetworkException(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        throw (NonSuccessfulResponseCodeException) e.getCause();
      } else if (e.getCause() instanceof PushNetworkException) {
        throw (PushNetworkException) e.getCause();
      } else {
        throw new PushNetworkException(e);
      }
    }
  }

  public ACI getAciByUsernameHash(String usernameHash) throws IOException {
    return this.pushServiceSocket.getAciByUsernameHash(usernameHash);
  }

  public ReserveUsernameResponse reserveUsername(List<String> usernameHashes) throws IOException {
    return this.pushServiceSocket.reserveUsername(usernameHashes);
  }

  public void confirmUsername(String username, ReserveUsernameResponse reserveUsernameResponse) throws IOException {
    this.pushServiceSocket.confirmUsername(username, reserveUsernameResponse);
  }

  public void deleteUsername() throws IOException {
    this.pushServiceSocket.deleteUsername();
  }

  public UsernameLinkComponents createUsernameLink(Username username) throws IOException {
    try {
      UsernameLink link     = username.generateLink();
      UUID         serverId = this.pushServiceSocket.createUsernameLink(Base64UrlSafe.encodeBytes(link.getEncryptedUsername()));

      return new UsernameLinkComponents(link.getEntropy(), serverId);
    } catch (BaseUsernameException e) {
      throw new AssertionError(e);
    }
  }

  public void deleteUsernameLink() throws IOException {
    this.pushServiceSocket.deleteUsernameLink();
  }

  public byte[] getEncryptedUsernameFromLinkServerId(UUID serverId) throws IOException {
    return this.pushServiceSocket.getEncryptedUsernameFromLinkServerId(serverId);
  }

  public void deleteAccount() throws IOException {
    this.pushServiceSocket.deleteAccount();
  }

  public void requestRateLimitPushChallenge() throws IOException {
    this.pushServiceSocket.requestRateLimitPushChallenge();
  }

  public void submitRateLimitPushChallenge(String challenge) throws IOException {
    this.pushServiceSocket.submitRateLimitPushChallenge(challenge);
  }

  public void submitRateLimitRecaptchaChallenge(String challenge, String recaptchaToken) throws IOException {
    this.pushServiceSocket.submitRateLimitRecaptchaChallenge(challenge, recaptchaToken);
  }

  public void cancelInFlightRequests() {
    this.pushServiceSocket.cancelInFlightRequests();
  }

  private String createDirectoryServerToken(String e164number, boolean urlSafe) {
    try {
      MessageDigest digest  = MessageDigest.getInstance("SHA1");
      byte[]        token   = Util.trim(digest.digest(e164number.getBytes()), 10);
      String        encoded = Base64.encodeBytesWithoutPadding(token);

      if (urlSafe) return encoded.replace('+', '-').replace('/', '_');
      else         return encoded;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(pushServiceSocket, groupsV2Operations);
  }

  /**
   * Helper class for holding the returns of getNewDeviceRegistration()
   */
  public static class NewDeviceRegistrationReturn {
    private final String          provisioningCode;
    private final IdentityKeyPair aciIdentity;
    private final IdentityKeyPair pniIdentity;
    private final String          number;
    private final ACI             aci;
    private final PNI             pni;
    private final ProfileKey      profileKey;
    private final boolean         readReceipts;

    NewDeviceRegistrationReturn(String provisioningCode, IdentityKeyPair aciIdentity, IdentityKeyPair pniIdentity, String number, ACI aci, PNI pni, ProfileKey profileKey, boolean readReceipts) {
      this.provisioningCode = provisioningCode;
      this.aciIdentity      = aciIdentity;
      this.pniIdentity      = pniIdentity;
      this.number           = number;
      this.aci              = aci;
      this.pni              = pni;
      this.profileKey       = profileKey;
      this.readReceipts     = readReceipts;
    }

    /**
     * @return The provisioning code to finish the new device registration
     */
    public String getProvisioningCode() {
      return provisioningCode;
    }

    /**
     * @return The account's permanent IdentityKeyPair
     */
    public IdentityKeyPair getAciIdentity() {
      return aciIdentity;
    }

    public IdentityKeyPair getPniIdentity() {
      return pniIdentity;
    }

    /**
     * @return The account's number
     */
    public String getNumber() {
      return number;
    }

    /**
     * @return The account's uuid
     */
    public ACI getAci() {
      return aci;
    }

    public PNI getPni() {
      return pni;
    }

    /**
     * @return The account's profile key or null
     */
    public ProfileKey getProfileKey() {
      return profileKey;
    }

    /**
     * @return The account's read receipts setting
     */
    public boolean isReadReceipts() {
      return readReceipts;
    }
  }

  public AuthCredentials getPaymentsAuthorization() throws IOException {
    return pushServiceSocket.getPaymentsAuthorization();
  }

  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode,
                                                    int signalProtocolRegistrationId,
                                                    boolean fetchesMessages,
                                                    byte[] unidentifiedAccessKey,
                                                    boolean unrestrictedUnidentifiedAccess,
                                                    AccountAttributes.Capabilities capabilities,
                                                    boolean discoverableByPhoneNumber,
                                                    byte[] encryptedDeviceName,
                                                    int pniRegistrationId)
      throws IOException
  {
    AccountAttributes accountAttributes = new AccountAttributes(
        null,
        signalProtocolRegistrationId,
        false,
        false,
        fetchesMessages,
        null,
        unidentifiedAccessKey,
        unrestrictedUnidentifiedAccess,
        discoverableByPhoneNumber,
        capabilities,
        Base64.encodeBytes(encryptedDeviceName),
        pniRegistrationId,
        null
    );

    return this.pushServiceSocket.verifySecondaryDevice(verificationCode, accountAttributes);
  }


}
