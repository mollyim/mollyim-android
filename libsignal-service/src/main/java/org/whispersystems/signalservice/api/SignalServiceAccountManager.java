/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.core.util.Base64;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.signal.libsignal.usernames.Username.UsernameLink;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.payments.CurrencyConversions;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.services.CdsiV2Service;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV3;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.CdsiAuthResponse;
import org.whispersystems.signalservice.internal.push.OneTimePreKeyCounts;
import org.whispersystems.signalservice.internal.push.PaymentAddress;
import org.whispersystems.signalservice.internal.push.ProfileAvatarData;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisioningSocket;
import org.whispersystems.signalservice.internal.push.ProvisioningUuid;
import org.whispersystems.signalservice.internal.push.ProvisioningVersion;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
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

  private final PushServiceSocket          pushServiceSocket;
  private final ProvisioningSocket         provisioningSocket;
  private final CredentialsProvider        credentials;
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
  public static SignalServiceAccountManager createWithStaticCredentials(SignalServiceConfiguration configuration,
                                                                        ACI aci,
                                                                        PNI pni,
                                                                        String e164,
                                                                        int deviceId,
                                                                        String password,
                                                                        String signalAgent,
                                                                        boolean automaticNetworkRetry,
                                                                        int maxGroupSize)
  {
    StaticCredentialsProvider credentialProvider = new StaticCredentialsProvider(aci, pni, e164, deviceId, password);
    GroupsV2Operations        gv2Operations      = new GroupsV2Operations(ClientZkOperations.create(configuration), maxGroupSize);

    return new SignalServiceAccountManager(
        new PushServiceSocket(configuration, credentialProvider, signalAgent, gv2Operations.getProfileOperations(), automaticNetworkRetry),
        new ProvisioningSocket(configuration, signalAgent),
        gv2Operations
    );
  }

  public SignalServiceAccountManager(PushServiceSocket pushServiceSocket, ProvisioningSocket provisioningSocket, GroupsV2Operations groupsV2Operations) {
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = pushServiceSocket;
    this.provisioningSocket = provisioningSocket;
    this.credentials        = pushServiceSocket.getCredentialsProvider();
    this.configuration      = pushServiceSocket.getConfiguration();
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

  public SecureValueRecoveryV3 getSecureValueRecoveryV3(Network network) {
    return new SecureValueRecoveryV3(network, pushServiceSocket);
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return this.pushServiceSocket.getWhoAmI();
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
   * @return True if the identifier corresponds to a registered user, otherwise false.
   */
  public boolean isIdentifierRegistered(ServiceId identifier) throws IOException {
    return pushServiceSocket.isIdentifierRegistered(identifier);
  }

  @SuppressWarnings("SameParameterValue")
  public CdsiV2Service.Response getRegisteredUsersWithCdsi(Set<String> previousE164s,
                                                           Set<String> newE164s,
                                                           Map<ServiceId, ProfileKey> serviceIds,
                                                           Optional<byte[]> token,
                                                           String mrEnclave,
                                                           Long timeoutMs,
                                                           @Nullable Network libsignalNetwork,
                                                           boolean useLibsignalRouteBasedCDSIConnectionLogic,
                                                           Consumer<byte[]> tokenSaver)
      throws IOException
  {
    CdsiAuthResponse                                auth    = pushServiceSocket.getCdsiAuth();
    CdsiV2Service                                   service = new CdsiV2Service(configuration, mrEnclave, libsignalNetwork, useLibsignalRouteBasedCDSIConnectionLogic);
    CdsiV2Service.Request                           request = new CdsiV2Service.Request(previousE164s, newE164s, serviceIds, token);
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

  /**
   * Request a UUID from the server for linking as a new device.
   * Called by the new device.
   * @return The UUID, Base64 encoded
   * @throws TimeoutException
   * @throws IOException
   */
  public ProvisioningUuid requestNewDeviceUuid() throws TimeoutException, IOException {
    return provisioningSocket.getProvisioningUuid();
  }

  /**
   * Gets info from the primary device to finish the registration as a new device.<br>
   * @param tempIdentity A temporary identity. Must be the same as the one given to the already verified device.
   * @return Contains the account's permanent IdentityKeyPair and it's number along with the provisioning code required to finish the registration.
   */
  public ProvisionDecryptResult getNewDeviceRegistration(IdentityKeyPair tempIdentity) throws TimeoutException, IOException {
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

    final boolean readReceipts = msg.readReceipts != null && msg.readReceipts;

    final MasterKey masterKey = (msg.masterKey != null) ? new MasterKey(msg.masterKey.toByteArray()) : null;

    return new ProvisionDecryptResult(
        msg.provisioningCode,
        aciIdentity, pniIdentity,
        number, aci, pni,
        profileKey,
        readReceipts,
        masterKey
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
  public int finishNewDeviceRegistration(String provisioningCode,
                                         AccountAttributes attributes,
                                         PreKeyCollection aciPreKeys, PreKeyCollection pniPreKeys,
                                         @Nullable String fcmToken)
      throws IOException, InvalidKeyException
  {
    int deviceId = this.pushServiceSocket.finishNewDeviceRegistration(provisioningCode, attributes, aciPreKeys, pniPreKeys, fcmToken);
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
                        MasterKey masterKey,
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
                                                            .provisioningVersion(ProvisioningVersion.CURRENT.getValue())
                                                            .masterKey(ByteString.of(masterKey.serialize()));

    byte[] ciphertext = cipher.encrypt(message.build());
    this.pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext);
  }

  public List<DeviceInfo> getDevices() throws IOException {
    return this.pushServiceSocket.getDevices();
  }

  public void removeDevice(int deviceId) throws IOException {
    this.pushServiceSocket.removeDevice(deviceId);
  }

  public List<TurnServerInfo> getTurnServerInfo() throws IOException {
    List<TurnServerInfo> relays = this.pushServiceSocket.getCallingRelays().getRelays();
    return relays != null ? relays : Collections.emptyList();
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
                                              List<String> visibleBadgeIds,
                                              boolean phoneNumberSharing)
      throws IOException
  {
    if (name == null) name = "";

    ProfileCipher     profileCipher                = new ProfileCipher(profileKey);
    byte[]            ciphertextName               = profileCipher.encryptString(name, ProfileCipher.getTargetNameLength(name));
    byte[]            ciphertextAbout              = profileCipher.encryptString(about, ProfileCipher.getTargetAboutLength(about));
    byte[]            ciphertextEmoji              = profileCipher.encryptString(aboutEmoji, ProfileCipher.EMOJI_PADDED_LENGTH);
    byte[]            ciphertextMobileCoinAddress  = paymentsAddress.map(address -> profileCipher.encryptWithLength(address.encode(), ProfileCipher.PAYMENTS_ADDRESS_CONTENT_SIZE)).orElse(null);
    byte[]            cipherTextPhoneNumberSharing = profileCipher.encryptBoolean(phoneNumberSharing);
    ProfileAvatarData profileAvatarData            = null;

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
                                                                             cipherTextPhoneNumberSharing,
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
      ProfileAndCredential credential = this.pushServiceSocket.retrieveVersionedProfileAndCredential(serviceId, profileKey, SealedSenderAccess.NONE, locale).get(10, TimeUnit.SECONDS);
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

  public ACI getAciByUsername(Username username) throws IOException {
    return this.pushServiceSocket.getAciByUsernameHash(Base64.encodeUrlSafeWithoutPadding(username.getHash()));
  }

  public ReserveUsernameResponse reserveUsername(List<String> usernameHashes) throws IOException {
    return this.pushServiceSocket.reserveUsername(usernameHashes);
  }

  public UsernameLinkComponents confirmUsernameAndCreateNewLink(Username username) throws IOException {
    try {
      UsernameLink link    = username.generateLink();
      UUID        serverId = this.pushServiceSocket.confirmUsernameAndCreateNewLink(username, link);

      return new UsernameLinkComponents(link.getEntropy(), serverId);
    } catch (BaseUsernameException e) {
      throw new AssertionError(e);
    }
  }

  public UsernameLinkComponents reclaimUsernameAndLink(Username username, UsernameLinkComponents linkComponents) throws IOException {
    try {
      UsernameLink link     = username.generateLink(linkComponents.getEntropy());
      UUID         serverId = this.pushServiceSocket.confirmUsernameAndCreateNewLink(username, link);

      return new UsernameLinkComponents(link.getEntropy(), serverId);
    } catch (BaseUsernameException e) {
      throw new AssertionError(e);
    }
  }

  public UsernameLinkComponents updateUsernameLink(UsernameLink newUsernameLink) throws IOException {
      UUID serverId = this.pushServiceSocket.createUsernameLink(Base64.encodeUrlSafeWithoutPadding(newUsernameLink.getEncryptedUsername()), true);

      return new UsernameLinkComponents(newUsernameLink.getEntropy(), serverId);
  }

  public void deleteUsername() throws IOException {
    this.pushServiceSocket.deleteUsername();
  }

  public UsernameLinkComponents createUsernameLink(Username username) throws IOException {
    try {
      UsernameLink link     = username.generateLink();
      UUID         serverId = this.pushServiceSocket.createUsernameLink(Base64.encodeUrlSafeWithPadding(link.getEncryptedUsername()), false);

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

  /**
   * Helper class for holding the returns of getNewDeviceRegistration()
   */
  public static class ProvisionDecryptResult {
    private final String          provisioningCode;
    private final IdentityKeyPair aciIdentity;
    private final IdentityKeyPair pniIdentity;
    private final String          number;
    private final ACI             aci;
    private final PNI             pni;
    private final ProfileKey      profileKey;
    private final boolean         readReceipts;
    private final MasterKey       masterKey;

    ProvisionDecryptResult(String provisioningCode, IdentityKeyPair aciIdentity, IdentityKeyPair pniIdentity, String number, ACI aci, PNI pni, ProfileKey profileKey, boolean readReceipts, MasterKey masterKey) {
      this.provisioningCode = provisioningCode;
      this.aciIdentity      = aciIdentity;
      this.pniIdentity      = pniIdentity;
      this.number           = number;
      this.aci              = aci;
      this.pni              = pni;
      this.profileKey       = profileKey;
      this.readReceipts     = readReceipts;
      this.masterKey        = masterKey;
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

    public MasterKey getMasterKey() {
      return masterKey;
    }
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(pushServiceSocket, groupsV2Operations);
  }

  public RegistrationApi getRegistrationApi() {
    return new RegistrationApi(pushServiceSocket);
  }

  public AuthCredentials getPaymentsAuthorization() throws IOException {
    return pushServiceSocket.getPaymentsAuthorization();
  }

}
