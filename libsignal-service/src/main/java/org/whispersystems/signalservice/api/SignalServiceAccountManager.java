/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.libsignal.net.Network;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.account.AccountApi;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV3;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;
import org.whispersystems.signalservice.internal.push.ProvisioningSocket;
import org.whispersystems.signalservice.internal.push.ProvisioningUuid;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The main interface for creating, registering, and
 * managing a Signal Service account.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAccountManager {

  private static final String TAG = SignalServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket                      pushServiceSocket;
  private final ProvisioningSocket                     provisioningSocket;
  private final CredentialsProvider                    credentials;
  private final GroupsV2Operations                     groupsV2Operations;
  private final SignalServiceConfiguration             configuration;
  private final SignalWebSocket.AuthenticatedWebSocket authWebSocket;
  private final AccountApi                             accountApi;

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
        null,
        null,
        new PushServiceSocket(configuration, credentialProvider, signalAgent, automaticNetworkRetry),
        new ProvisioningSocket(configuration, signalAgent),
        gv2Operations
    );
  }

  public SignalServiceAccountManager(@Nullable SignalWebSocket.AuthenticatedWebSocket authWebSocket,
                                     @Nullable AccountApi accountApi,
                                     @Nonnull PushServiceSocket pushServiceSocket,
                                     @Nullable ProvisioningSocket provisioningSocket,
                                     @Nonnull GroupsV2Operations groupsV2Operations) {
    this.authWebSocket      = authWebSocket;
    this.accountApi         = accountApi;
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = pushServiceSocket;
    this.provisioningSocket = provisioningSocket;
    this.credentials        = pushServiceSocket.getCredentialsProvider();
    this.configuration      = pushServiceSocket.getConfiguration();
  }

  public SecureValueRecoveryV2 getSecureValueRecoveryV2(String mrEnclave) {
    return new SecureValueRecoveryV2(configuration, mrEnclave, authWebSocket);
  }

  public SecureValueRecoveryV3 getSecureValueRecoveryV3(Network network) {
    return new SecureValueRecoveryV3(network, authWebSocket);
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return NetworkResultUtil.toBasicLegacy(accountApi.whoAmI());
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
      byte[] type = { Curve.DJB_TYPE };
      publicKeyBytes = ByteUtil.combine(type, publicKeyBytes);
    }
    final ECPublicKey  publicKey;
    final ECPrivateKey privateKey;
    try {
      publicKey  = Curve.decodePoint(publicKeyBytes, 0);
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

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
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
    return new GroupsV2Api(authWebSocket, pushServiceSocket, groupsV2Operations);
  }

  public RegistrationApi getRegistrationApi() {
    return new RegistrationApi(pushServiceSocket);
  }
}
