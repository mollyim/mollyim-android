/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.squareup.wire.Message;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialRequest;
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialResponse;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyVersion;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse;
import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest;
import org.whispersystems.signalservice.api.account.PniKeyDistributionRequest;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.groupsv2.CredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.VerifyDeviceResponse;
import org.whispersystems.signalservice.api.payments.CurrencyConversions;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.push.exceptions.ContactManifestMismatchException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ExternalServiceFailureException;
import org.whispersystems.signalservice.api.push.exceptions.HttpConflictException;
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException;
import org.whispersystems.signalservice.api.push.exceptions.InvalidTransportModeException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.MustRequestNewCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResumableUploadResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.PushChallengeRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RangeException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.RegistrationRetryException;
import org.whispersystems.signalservice.api.push.exceptions.RemoteAttestationResponseExpiredException;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotAssociatedWithAnAccountException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotReservedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException;
import org.whispersystems.signalservice.api.storage.StorageAuthResponse;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.subscriptions.PayPalConfirmPaymentIntentResponse;
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse;
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse;
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest;
import org.whispersystems.signalservice.internal.push.exceptions.ForbiddenException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupExistsException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupNotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupStaleDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;
import org.whispersystems.signalservice.internal.push.exceptions.PaymentsRegionException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.push.http.AcceptLanguagesUtil;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.DigestingRequestBody;
import org.whispersystems.signalservice.internal.push.http.NoCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation;
import org.whispersystems.signalservice.internal.storage.protos.StorageItems;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProxySelector;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @author Moxie Marlinspike
 */
public class PushServiceSocket {

  private static final String TAG = PushServiceSocket.class.getSimpleName();

  private static final String VERIFY_ACCOUNT_CODE_PATH   = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH          = "/v1/accounts/gcm/";
  private static final String TURN_SERVER_INFO           = "/v1/accounts/turn";
  private static final String SET_ACCOUNT_ATTRIBUTES     = "/v1/accounts/attributes/";
  private static final String PIN_PATH                   = "/v1/accounts/pin/";
  private static final String REGISTRATION_LOCK_PATH     = "/v1/accounts/registration_lock";
  private static final String WHO_AM_I                   = "/v1/accounts/whoami";
  private static final String GET_USERNAME_PATH          = "/v1/accounts/username_hash/%s";
  private static final String MODIFY_USERNAME_PATH       = "/v1/accounts/username_hash";
  private static final String RESERVE_USERNAME_PATH      = "/v1/accounts/username_hash/reserve";
  private static final String CONFIRM_USERNAME_PATH      = "/v1/accounts/username_hash/confirm";
  private static final String USERNAME_LINK_PATH         = "/v1/accounts/username_link";
  private static final String USERNAME_FROM_LINK_PATH    = "/v1/accounts/username_link/%s";
  private static final String DELETE_ACCOUNT_PATH        = "/v1/accounts/me";
  private static final String CHANGE_NUMBER_PATH         = "/v2/accounts/number";
  private static final String IDENTIFIER_REGISTERED_PATH = "/v1/accounts/account/%s";
  private static final String REQUEST_ACCOUNT_DATA_PATH  = "/v2/accounts/data_report";
  private static final String PNI_KEY_DISTRUBTION_PATH   = "/v2/accounts/phone_number_identity_key_distribution";

  private static final String PREKEY_METADATA_PATH      = "/v2/keys?identity=%s";
  private static final String PREKEY_PATH               = "/v2/keys?identity=%s";
  private static final String PREKEY_DEVICE_PATH        = "/v2/keys/%s/%s?pq=true";
  private static final String SIGNED_PREKEY_PATH        = "/v2/keys/signed?identity=%s";

  private static final String PROVISIONING_CODE_PATH    = "/v1/devices/provisioning/code";
  private static final String PROVISIONING_MESSAGE_PATH = "/v1/provisioning/%s";
  private static final String DEVICE_PATH               = "/v1/devices/%s";

  private static final String DIRECTORY_AUTH_PATH       = "/v1/directory/auth";
  private static final String MESSAGE_PATH              = "/v1/messages/%s";
  private static final String GROUP_MESSAGE_PATH        = "/v1/messages/multi_recipient?ts=%s&online=%s&urgent=%s&story=%s";
  private static final String SENDER_ACK_MESSAGE_PATH   = "/v1/messages/%s/%d";
  private static final String UUID_ACK_MESSAGE_PATH     = "/v1/messages/uuid/%s";
  private static final String ATTACHMENT_V2_PATH        = "/v2/attachments/form/upload";
  private static final String ATTACHMENT_V4_PATH        = "/v4/attachments/form/upload";

  private static final String PAYMENTS_AUTH_PATH        = "/v1/payments/auth";

  private static final String PROFILE_PATH              = "/v1/profile/%s";
  private static final String PROFILE_BATCH_CHECK_PATH  = "/v1/profile/identity_check/batch";

  private static final String SENDER_CERTIFICATE_PATH         = "/v1/certificate/delivery";
  private static final String SENDER_CERTIFICATE_NO_E164_PATH = "/v1/certificate/delivery?includeE164=false";

  private static final String KBS_AUTH_PATH                  = "/v1/backup/auth";
  private static final String KBS_AUTH_CHECK_PATH            = "/v1/backup/auth/check";

  private static final String ATTACHMENT_KEY_DOWNLOAD_PATH   = "attachments/%s";
  private static final String ATTACHMENT_ID_DOWNLOAD_PATH    = "attachments/%d";
  private static final String ATTACHMENT_UPLOAD_PATH         = "attachments/";
  private static final String AVATAR_UPLOAD_PATH             = "";

  private static final String STICKER_MANIFEST_PATH          = "stickers/%s/manifest.proto";
  private static final String STICKER_PATH                   = "stickers/%s/full/%d";

  private static final String GROUPSV2_CREDENTIAL       = "/v1/certificate/auth/group?redemptionStartSeconds=%d&redemptionEndSeconds=%d&pniAsServiceId=true";
  private static final String GROUPSV2_GROUP            = "/v1/groups/";
  private static final String GROUPSV2_GROUP_PASSWORD   = "/v1/groups/?inviteLinkPassword=%s";
  private static final String GROUPSV2_GROUP_CHANGES    = "/v1/groups/logs/%s?maxSupportedChangeEpoch=%d&includeFirstState=%s&includeLastState=false";
  private static final String GROUPSV2_AVATAR_REQUEST   = "/v1/groups/avatar/form";
  private static final String GROUPSV2_GROUP_JOIN       = "/v1/groups/join/%s";
  private static final String GROUPSV2_TOKEN            = "/v1/groups/token";

  private static final String PAYMENTS_CONVERSIONS      = "/v1/payments/conversions";


  private static final String SUBMIT_RATE_LIMIT_CHALLENGE       = "/v1/challenge";
  private static final String REQUEST_RATE_LIMIT_PUSH_CHALLENGE = "/v1/challenge/push";

  private static final String DONATION_REDEEM_RECEIPT = "/v1/donation/redeem-receipt";

  private static final String UPDATE_SUBSCRIPTION_LEVEL                  = "/v1/subscription/%s/level/%s/%s/%s";
  private static final String SUBSCRIPTION                               = "/v1/subscription/%s";
  private static final String CREATE_STRIPE_SUBSCRIPTION_PAYMENT_METHOD  = "/v1/subscription/%s/create_payment_method";
  private static final String CREATE_PAYPAL_SUBSCRIPTION_PAYMENT_METHOD  = "/v1/subscription/%s/create_payment_method/paypal";
  private static final String DEFAULT_STRIPE_SUBSCRIPTION_PAYMENT_METHOD = "/v1/subscription/%s/default_payment_method/stripe/%s";
  private static final String DEFAULT_PAYPAL_SUBSCRIPTION_PAYMENT_METHOD = "/v1/subscription/%s/default_payment_method/braintree/%s";
  private static final String SUBSCRIPTION_RECEIPT_CREDENTIALS           = "/v1/subscription/%s/receipt_credentials";
  private static final String CREATE_STRIPE_ONE_TIME_PAYMENT_INTENT      = "/v1/subscription/boost/create";
  private static final String CREATE_PAYPAL_ONE_TIME_PAYMENT_INTENT      = "/v1/subscription/boost/paypal/create";
  private static final String CONFIRM_PAYPAL_ONE_TIME_PAYMENT_INTENT     = "/v1/subscription/boost/paypal/confirm";
  private static final String BOOST_RECEIPT_CREDENTIALS                  = "/v1/subscription/boost/receipt_credentials";
  private static final String DONATIONS_CONFIGURATION                    = "/v1/subscription/configuration";

  private static final String VERIFICATION_SESSION_PATH = "/v1/verification/session";
  private static final String VERIFICATION_CODE_PATH    = "/v1/verification/session/%s/code";

  private static final String REGISTRATION_PATH    = "/v1/registration";

  private static final String CDSI_AUTH = "/v2/directory/auth";
  private static final String SVR2_AUTH = "/v2/backup/auth";

  private static final String REPORT_SPAM = "/v1/messages/report/%s/%s";

  private static final String BACKUP_AUTH_CHECK = "/v2/backup/auth/check";

  private static final String CALL_LINK_CREATION_AUTH = "/v1/call-link/create-auth";
  private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";

  private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER = new EmptyResponseCodeHandler();

  private static final long CDN2_RESUMABLE_LINK_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(7);

  private static final int MAX_FOLLOW_UPS = 20;

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final ServiceConnectionHolder[]        serviceClients;
  private final Map<Integer, ConnectionHolder[]> cdnClientsMap;
  private final ConnectionHolder[]               keyBackupServiceClients;
  private final ConnectionHolder[]               storageClients;

  private final CredentialsProvider              credentialsProvider;
  private final String                           signalAgent;
  private final SecureRandom                     random;
  private final ClientZkProfileOperations        clientZkProfileOperations;
  private final boolean                          automaticNetworkRetry;

  public PushServiceSocket(SignalServiceConfiguration configuration,
                           CredentialsProvider credentialsProvider,
                           String signalAgent,
                           ClientZkProfileOperations clientZkProfileOperations,
                           boolean automaticNetworkRetry)
  {
    this.credentialsProvider       = credentialsProvider;
    this.signalAgent               = signalAgent;
    this.automaticNetworkRetry     = automaticNetworkRetry;
    this.serviceClients            = createServiceConnectionHolders(configuration.getSignalServiceUrls(), configuration.getNetworkInterceptors(), configuration.getSocketFactory(), configuration.getProxySelector(), configuration.getDns());
    this.cdnClientsMap             = createCdnClientsMap(configuration.getSignalCdnUrlMap(), configuration.getNetworkInterceptors(), configuration.getSocketFactory(), configuration.getProxySelector(), configuration.getDns());
    this.keyBackupServiceClients   = createConnectionHolders(configuration.getSignalKeyBackupServiceUrls(), configuration.getNetworkInterceptors(), configuration.getSocketFactory(), configuration.getProxySelector(), configuration.getDns());
    this.storageClients            = createConnectionHolders(configuration.getSignalStorageUrls(), configuration.getNetworkInterceptors(), configuration.getSocketFactory(), configuration.getProxySelector(), configuration.getDns());
    this.random                    = new SecureRandom();
    this.clientZkProfileOperations = clientZkProfileOperations;
  }

  public RegistrationSessionMetadataResponse createVerificationSession(@Nullable String pushToken, @Nullable String mcc, @Nullable String mnc) throws IOException {
    final String jsonBody = JsonUtil.toJson(new VerificationSessionMetadataRequestBody(credentialsProvider.getE164(), pushToken, mcc, mnc));
    try (Response response = makeServiceRequest(VERIFICATION_SESSION_PATH, "POST", jsonRequestBody(jsonBody), NO_HEADERS, new RegistrationSessionResponseHandler(), Optional.empty(), false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse getSessionStatus(String sessionId) throws IOException {
    String path = VERIFICATION_SESSION_PATH + "/" + sessionId;

    try (Response response = makeServiceRequest(path, "GET", jsonRequestBody(null), NO_HEADERS, new RegistrationSessionResponseHandler(), Optional.empty(), false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse patchVerificationSession(String sessionId, @Nullable String pushToken, @Nullable String mcc, @Nullable String mnc, @Nullable String captchaToken, @Nullable String pushChallengeToken) throws IOException {
    String path = VERIFICATION_SESSION_PATH + "/" + sessionId;

    final UpdateVerificationSessionRequestBody requestBody = new UpdateVerificationSessionRequestBody(captchaToken, pushToken, pushChallengeToken, mcc, mnc);
    try (Response response = makeServiceRequest(path, "PATCH", jsonRequestBody(JsonUtil.toJson(requestBody)), NO_HEADERS, new PatchRegistrationSessionResponseHandler(), Optional.empty(), false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse requestVerificationCode(String sessionId, Locale locale, boolean androidSmsRetriever, VerificationCodeTransport transport) throws IOException {
    String path = String.format(VERIFICATION_CODE_PATH, sessionId);
    Map<String, String> headers = locale != null ? Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry()) : NO_HEADERS;
    Map<String, String> body    = new HashMap<>();

    switch (transport) {
      case SMS:
        body.put("transport", "sms");
        break;
      case VOICE:
        body.put("transport", "voice");
        break;
    }

    body.put("client", androidSmsRetriever ? "android-2021-03" : "android");

    try (Response response = makeServiceRequest(path, "POST", jsonRequestBody(JsonUtil.toJson(body)), headers, new RegistrationCodeRequestResponseHandler(), Optional.empty(), false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse submitVerificationCode(String sessionId, String verificationCode) throws IOException {
    String path = String.format(VERIFICATION_CODE_PATH, sessionId);
    Map<String, String> body =  new HashMap<>();
    body.put("code", verificationCode);
    try (Response response = makeServiceRequest(path, "PUT", jsonRequestBody(JsonUtil.toJson(body)), NO_HEADERS, new RegistrationCodeSubmissionResponseHandler(), Optional.empty(), false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public VerifyAccountResponse submitRegistrationRequest(@Nullable String sessionId, @Nullable String recoveryPassword, AccountAttributes attributes, PreKeyCollection aciPreKeys, PreKeyCollection pniPreKeys, @Nullable String fcmToken, boolean skipDeviceTransfer) throws IOException {
    String path = REGISTRATION_PATH;
    if (sessionId == null && recoveryPassword == null) {
      throw new IllegalArgumentException("Neither Session ID nor Recovery Password provided.");
    }

    if (sessionId != null && recoveryPassword != null) {
      throw new IllegalArgumentException("You must supply one and only one of either: Session ID, or Recovery Password.");
    }

    GcmRegistrationId gcmRegistrationId;
    if (attributes.getFetchesMessages()) {
      gcmRegistrationId = null;
    } else {
      gcmRegistrationId = new GcmRegistrationId(fcmToken, true);
    }

    final SignedPreKeyEntity aciSignedPreKey = new SignedPreKeyEntity(Objects.requireNonNull(aciPreKeys.getSignedPreKey()).getId(),
                                                                      aciPreKeys.getSignedPreKey().getKeyPair().getPublicKey(),
                                                                      aciPreKeys.getSignedPreKey().getSignature());
    final SignedPreKeyEntity pniSignedPreKey = new SignedPreKeyEntity(Objects.requireNonNull(pniPreKeys.getSignedPreKey()).getId(),
                                                                      pniPreKeys.getSignedPreKey().getKeyPair().getPublicKey(),
                                                                      pniPreKeys.getSignedPreKey().getSignature());
    final KyberPreKeyEntity aciLastResortKyberPreKey = new KyberPreKeyEntity(Objects.requireNonNull(aciPreKeys.getLastResortKyberPreKey()).getId(),
                                                                             aciPreKeys.getLastResortKyberPreKey().getKeyPair().getPublicKey(),
                                                                             aciPreKeys.getLastResortKyberPreKey().getSignature());
    final KyberPreKeyEntity pniLastResortKyberPreKey = new KyberPreKeyEntity(Objects.requireNonNull(pniPreKeys.getLastResortKyberPreKey()).getId(),
                                                                             pniPreKeys.getLastResortKyberPreKey().getKeyPair().getPublicKey(),
                                                                             pniPreKeys.getLastResortKyberPreKey().getSignature());
    RegistrationSessionRequestBody body = new RegistrationSessionRequestBody(sessionId,
                                                                             recoveryPassword,
                                                                             attributes,
                                                                             Base64.encodeBytesWithoutPadding(aciPreKeys.getIdentityKey().serialize()),
                                                                             Base64.encodeBytesWithoutPadding(pniPreKeys.getIdentityKey().serialize()),
                                                                             aciSignedPreKey,
                                                                             pniSignedPreKey,
                                                                             aciLastResortKyberPreKey,
                                                                             pniLastResortKyberPreKey,
                                                                             gcmRegistrationId,
                                                                             skipDeviceTransfer,
                                                                             true);

    String response = makeServiceRequest(path, "POST", JsonUtil.toJson(body), NO_HEADERS, new RegistrationSessionResponseHandler(), Optional.empty());
    return JsonUtil.fromJson(response, VerifyAccountResponse.class);
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return JsonUtil.fromJson(makeServiceRequest(WHO_AM_I, "GET", null), WhoAmIResponse.class);
  }

  public boolean isIdentifierRegistered(ServiceId identifier) throws IOException {
    try {
      makeServiceRequestWithoutAuthentication(String.format(IDENTIFIER_REGISTERED_PATH, identifier.toString()), "HEAD", null);
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }

  public String getAccountDataReport() throws IOException {
    return makeServiceRequest(REQUEST_ACCOUNT_DATA_PATH, "GET", null);
  }

  public CdsiAuthResponse getCdsiAuth() throws IOException {
    String body = makeServiceRequest(CDSI_AUTH, "GET", null);
    return JsonUtil.fromJsonResponse(body, CdsiAuthResponse.class);
  }

  public AuthCredentials getSvr2Authorization() throws IOException {
    String          body        = makeServiceRequest(SVR2_AUTH, "GET", null);
    AuthCredentials credentials = JsonUtil.fromJsonResponse(body, AuthCredentials.class);

    return credentials;
  }

  public VerifyAccountResponse changeNumber(@Nonnull ChangePhoneNumberRequest changePhoneNumberRequest)
      throws IOException
  {
    String requestBody  = JsonUtil.toJson(changePhoneNumberRequest);
    String responseBody = makeServiceRequest(CHANGE_NUMBER_PATH, "PUT", requestBody);

    return JsonUtil.fromJson(responseBody, VerifyAccountResponse.class);
  }

  public VerifyAccountResponse distributePniKeys(@NonNull PniKeyDistributionRequest distributionRequest) throws IOException {
    String request  = JsonUtil.toJson(distributionRequest);
    String response = makeServiceRequest(PNI_KEY_DISTRUBTION_PATH, "PUT", request);

    return JsonUtil.fromJson(response, VerifyAccountResponse.class);
  }

  public void setAccountAttributes(@Nonnull AccountAttributes accountAttributes)
      throws IOException
  {
    makeServiceRequest(SET_ACCOUNT_ATTRIBUTES, "PUT", JsonUtil.toJson(accountAttributes));
  }

  public int finishNewDeviceRegistration(String code, ConfirmCodeMessage confirmCodeMessage) throws IOException {
    String json = JsonUtil.toJson(confirmCodeMessage);
    String responseText = makeServiceRequest(String.format(DEVICE_PATH, code), "PUT", json);
    DeviceId response = JsonUtil.fromJson(responseText, DeviceId.class);
    return response.getDeviceId();
  }

  public String getNewDeviceVerificationCode() throws IOException {
    String responseText = makeServiceRequest(PROVISIONING_CODE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, DeviceCode.class).getVerificationCode();
  }

  public List<DeviceInfo> getDevices() throws IOException {
    String responseText = makeServiceRequest(String.format(DEVICE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, DeviceInfoList.class).getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    makeServiceRequest(String.format(DEVICE_PATH, String.valueOf(deviceId)), "DELETE", null);
  }

  public void sendProvisioningMessage(String destination, byte[] body) throws IOException {
    makeServiceRequest(String.format(PROVISIONING_MESSAGE_PATH, destination), "PUT",
                       JsonUtil.toJson(new ProvisioningMessage(Base64.encodeBytes(body))));
  }

  public void registerGcmId(@Nonnull String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId, true);
    makeServiceRequest(REGISTER_GCM_PATH, "PUT", JsonUtil.toJson(registration));
  }

  public void unregisterGcmId() throws IOException {
    makeServiceRequest(REGISTER_GCM_PATH, "DELETE", null);
  }

  public void requestPushChallenge(String sessionId, String gcmRegistrationId) throws IOException {
    patchVerificationSession(sessionId, gcmRegistrationId, null, null, null, null);
  }

  /** Note: Setting a KBS Pin will clear this */
  public void removeRegistrationLockV1() throws IOException {
    makeServiceRequest(PIN_PATH, "DELETE", null);
  }

  public void setRegistrationLockV2(String registrationLock) throws IOException {
    RegistrationLockV2 accountLock = new RegistrationLockV2(registrationLock);
    makeServiceRequest(REGISTRATION_LOCK_PATH, "PUT", JsonUtil.toJson(accountLock));
  }

  public void disableRegistrationLockV2() throws IOException {
    makeServiceRequest(REGISTRATION_LOCK_PATH, "DELETE", null);
  }

  public byte[] getSenderCertificate() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, SenderCertificate.class).getCertificate();
  }

  public byte[] getUuidOnlySenderCertificate() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_NO_E164_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, SenderCertificate.class).getCertificate();
  }

  public SendGroupMessageResponse sendGroupMessage(byte[] body, byte[] joinedUnidentifiedAccess, long timestamp, boolean online, boolean urgent, boolean story)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);

    String path = String.format(Locale.US, GROUP_MESSAGE_PATH, timestamp, online, urgent, story);

    Request.Builder requestBuilder = new Request.Builder();
    requestBuilder.url(String.format("%s%s", connectionHolder.getUrl(), path));
    requestBuilder.put(RequestBody.create(MediaType.get("application/vnd.signal-messenger.mrm"), body));
    requestBuilder.addHeader("Unidentified-Access-Key", Base64.encodeBytes(joinedUnidentifiedAccess));

    if (signalAgent != null) {
      requestBuilder.addHeader("X-Signal-Agent", signalAgent);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      requestBuilder.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    Call call = connectionHolder.getUnidentifiedClient().newCall(requestBuilder.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      switch (response.code()) {
        case 200:
          return readBodyJson(response.body(), SendGroupMessageResponse.class);
        case 401:
          throw new InvalidUnidentifiedAccessHeaderException();
        case 404:
          throw new NotFoundException("At least one unregistered user in message send.");
        case 409:
          GroupMismatchedDevices[] mismatchedDevices = readBodyJson(response.body(), GroupMismatchedDevices[].class);
          throw new GroupMismatchedDevicesException(mismatchedDevices);
        case 410:
          GroupStaleDevices[] staleDevices = readBodyJson(response.body(), GroupStaleDevices[].class);
          throw new GroupStaleDevicesException(staleDevices);
        case 508:
          throw new ServerRejectedException();
        default:
          throw new NonSuccessfulResponseCodeException(response.code());
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException | MalformedResponseException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  public SendMessageResponse sendMessage(OutgoingPushMessageList bundle, Optional<UnidentifiedAccess> unidentifiedAccess, boolean story)
      throws IOException
  {
    try {
      String              responseText = makeServiceRequest(String.format("/v1/messages/%s?story=%s", bundle.getDestination(), story ? "true" : "false"), "PUT", JsonUtil.toJson(bundle), NO_HEADERS, NO_HANDLER, unidentifiedAccess);
      SendMessageResponse response     = JsonUtil.fromJson(responseText, SendMessageResponse.class);

      response.setSentUnidentfied(unidentifiedAccess.isPresent());

      return response;
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(bundle.getDestination(), nfe);
    }
  }

  public SignalServiceMessagesResult getMessages(boolean allowStories) throws IOException {
    Map<String, String> headers = Collections.singletonMap("X-Signal-Receive-Stories", allowStories ? "true" : "false");

    try (Response response = makeServiceRequest(String.format(MESSAGE_PATH, ""), "GET", (RequestBody) null, headers, NO_HANDLER, Optional.empty(), false)) {
      validateServiceResponse(response);

      List<SignalServiceEnvelopeEntity> envelopes = readBodyJson(response.body(), SignalServiceEnvelopeEntityList.class).getMessages();

      long serverDeliveredTimestamp = 0;
      try {
        String stringValue = response.header(SERVER_DELIVERED_TIMESTAMP_HEADER);
        stringValue = stringValue != null ? stringValue : "0";

        serverDeliveredTimestamp = Long.parseLong(stringValue);
      } catch (NumberFormatException e) {
        Log.w(TAG, e);
      }

      return new SignalServiceMessagesResult(envelopes, serverDeliveredTimestamp);
    }
  }

  public void acknowledgeMessage(String sender, long timestamp) throws IOException {
    makeServiceRequest(String.format(Locale.US, SENDER_ACK_MESSAGE_PATH, sender, timestamp), "DELETE", null);
  }

  public void acknowledgeMessage(String uuid) throws IOException {
    makeServiceRequest(String.format(UUID_ACK_MESSAGE_PATH, uuid), "DELETE", null);
  }

  public void registerPreKeys(PreKeyUpload preKeyUpload)
      throws IOException
  {
    SignedPreKeyEntity      signedPreKey          = null;
    List<PreKeyEntity>      oneTimeEcPreKeys      = null;
    KyberPreKeyEntity       lastResortKyberPreKey = null;
    List<KyberPreKeyEntity> oneTimeKyberPreKeys   = null;

    if (preKeyUpload.getSignedPreKey() != null) {
      signedPreKey = new SignedPreKeyEntity(preKeyUpload.getSignedPreKey().getId(),
                                            preKeyUpload.getSignedPreKey().getKeyPair().getPublicKey(),
                                            preKeyUpload.getSignedPreKey().getSignature());
    }

    if (preKeyUpload.getOneTimeEcPreKeys() != null) {
      oneTimeEcPreKeys = preKeyUpload
          .getOneTimeEcPreKeys()
          .stream()
          .map(it -> {
            try {
              return new PreKeyEntity(it.getId(), it.getKeyPair().getPublicKey());
            } catch (InvalidKeyException e) {
              throw new AssertionError("unexpected invalid key", e);
            }
          })
          .collect(Collectors.toList());
    }

    if (preKeyUpload.getLastResortKyberPreKey() != null) {
      lastResortKyberPreKey = new KyberPreKeyEntity(preKeyUpload.getLastResortKyberPreKey().getId(),
                                                    preKeyUpload.getLastResortKyberPreKey().getKeyPair().getPublicKey(),
                                                    preKeyUpload.getLastResortKyberPreKey().getSignature());
    }

    if (preKeyUpload.getOneTimeKyberPreKeys() != null) {
      oneTimeKyberPreKeys = preKeyUpload
          .getOneTimeKyberPreKeys()
          .stream()
          .map(it -> new KyberPreKeyEntity(it.getId(), it.getKeyPair().getPublicKey(), it.getSignature()))
          .collect(Collectors.toList());
    }

    makeServiceRequest(String.format(Locale.US, PREKEY_PATH, preKeyUpload.getServiceIdType().queryParam()),
                       "PUT",
                       JsonUtil.toJson(new PreKeyState(preKeyUpload.getIdentityKey(),
                                                       signedPreKey,
                                                       oneTimeEcPreKeys,
                                                       lastResortKyberPreKey,
                                                       oneTimeKyberPreKeys)));
  }

  public OneTimePreKeyCounts getAvailablePreKeys(ServiceIdType serviceIdType) throws IOException {
    String              path         = String.format(PREKEY_METADATA_PATH, serviceIdType.queryParam());
    String              responseText = makeServiceRequest(path, "GET", null);
    OneTimePreKeyCounts preKeyStatus = JsonUtil.fromJson(responseText, OneTimePreKeyCounts.class);

    return preKeyStatus;
  }

  /**
   * Retrieves prekeys. If the specified device is the primary (i.e. deviceId 1), it will retrieve prekeys
   * for all devices. If it is not a primary, it will only contain the prekeys for that specific device.
   */
  public List<PreKeyBundle> getPreKeys(SignalServiceAddress destination,
                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                       int deviceId)
      throws IOException
  {
    return getPreKeysBySpecifier(destination, unidentifiedAccess, deviceId == 1 ? "*" : String.valueOf(deviceId));
  }

  /**
   * Retrieves a prekey for a specific device.
   */
  public PreKeyBundle getPreKey(SignalServiceAddress destination, int deviceId) throws IOException {
    List<PreKeyBundle> bundles = getPreKeysBySpecifier(destination, Optional.empty(), String.valueOf(deviceId));

    if (bundles.size() > 0) {
      return bundles.get(0);
    } else {
      throw new IOException("No prekeys available!");
    }
  }

  private List<PreKeyBundle> getPreKeysBySpecifier(SignalServiceAddress destination,
                                                   Optional<UnidentifiedAccess> unidentifiedAccess,
                                                   String deviceSpecifier)
      throws IOException
  {
    try {
      String path = String.format(PREKEY_DEVICE_PATH, destination.getIdentifier(), deviceSpecifier);

      Log.d(TAG, "Fetching prekeys for " + destination.getIdentifier() + "." + deviceSpecifier + ", i.e. GET " + path);

      String             responseText = makeServiceRequest(path, "GET", null, NO_HEADERS, NO_HANDLER, unidentifiedAccess);
      PreKeyResponse     response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);
      List<PreKeyBundle> bundles      = new LinkedList<>();

      for (PreKeyResponseItem device : response.getDevices()) {
        ECPublicKey  preKey                = null;
        ECPublicKey  signedPreKey          = null;
        byte[]       signedPreKeySignature = null;
        int          preKeyId              = PreKeyBundle.NULL_PRE_KEY_ID;
        int          signedPreKeyId        = PreKeyBundle.NULL_PRE_KEY_ID;
        int          kyberPreKeyId         = PreKeyBundle.NULL_PRE_KEY_ID;
        KEMPublicKey kyberPreKey           = null;
        byte[]       kyberPreKeySignature  = null;

        if (device.getSignedPreKey() != null) {
          signedPreKey          = device.getSignedPreKey().getPublicKey();
          signedPreKeyId        = device.getSignedPreKey().getKeyId();
          signedPreKeySignature = device.getSignedPreKey().getSignature();
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().getKeyId();
          preKey   = device.getPreKey().getPublicKey();
        }

        if (device.getKyberPreKey() != null) {
          kyberPreKey          = device.getKyberPreKey().getPublicKey();
          kyberPreKeyId        = device.getKyberPreKey().getKeyId();
          kyberPreKeySignature = device.getKyberPreKey().getSignature();
        }

        bundles.add(new PreKeyBundle(device.getRegistrationId(),
                                     device.getDeviceId(),
                                     preKeyId,
                                     preKey,
                                     signedPreKeyId,
                                     signedPreKey,
                                     signedPreKeySignature,
                                     response.getIdentityKey(),
                                     kyberPreKeyId,
                                     kyberPreKey,
                                     kyberPreKeySignature));
      }

      return bundles;
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getIdentifier(), nfe);
    }
  }

  public void setCurrentSignedPreKey(ServiceIdType serviceIdType, SignedPreKeyRecord signedPreKey) throws IOException {
    String             path               = String.format(SIGNED_PREKEY_PATH, serviceIdType.queryParam());
    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());
    makeServiceRequest(path, "PUT", JsonUtil.toJson(signedPreKeyEntity));
  }

  public void retrieveAttachment(int cdnNumber, SignalServiceAttachmentRemoteId cdnPath, File destination, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    final String path;
    if (cdnPath.getV2().isPresent()) {
      path = String.format(Locale.US, ATTACHMENT_ID_DOWNLOAD_PATH, cdnPath.getV2().get());
    } else {
      path = String.format(Locale.US, ATTACHMENT_KEY_DOWNLOAD_PATH, cdnPath.getV3().get());
    }
    downloadFromCdn(destination, cdnNumber, path, maxSizeBytes, listener);
  }

  public byte[] retrieveSticker(byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    try {
      downloadFromCdn(output, 0, 0, String.format(Locale.US, STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }

    return output.toByteArray();
  }

  public byte[] retrieveStickerManifest(byte[] packId)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    try {
      downloadFromCdn(output, 0, 0, String.format(STICKER_MANIFEST_PATH, hexPackId), 1024 * 1024, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }

    return output.toByteArray();
  }

  public ListenableFuture<SignalServiceProfile> retrieveProfile(SignalServiceAddress target, Optional<UnidentifiedAccess> unidentifiedAccess, Locale locale) {
    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, target.getIdentifier()), "GET", null, AcceptLanguagesUtil.getHeadersWithAcceptLanguage(locale), unidentifiedAccess);

    return FutureTransformers.map(response, body -> {
      try {
        return JsonUtil.fromJson(body, SignalServiceProfile.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new MalformedResponseException("Unable to parse entity", e);
      }
    });
  }

  public ListenableFuture<ProfileAndCredential> retrieveVersionedProfileAndCredential(ACI target, ProfileKey profileKey, Optional<UnidentifiedAccess> unidentifiedAccess, Locale locale) {
    ProfileKeyVersion                  profileKeyIdentifier = profileKey.getProfileKeyVersion(target.getLibSignalAci());
    ProfileKeyCredentialRequestContext requestContext       = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, target.getLibSignalAci(), profileKey);
    ProfileKeyCredentialRequest        request              = requestContext.getRequest();

    String version           = profileKeyIdentifier.serialize();
    String credentialRequest = Hex.toStringCondensed(request.serialize());
    String subPath           = String.format("%s/%s/%s?credentialType=expiringProfileKey", target, version, credentialRequest);


    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, subPath), "GET", null, AcceptLanguagesUtil.getHeadersWithAcceptLanguage(locale), unidentifiedAccess);

    return FutureTransformers.map(response, body -> formatProfileAndCredentialBody(requestContext, body));
  }

  private ProfileAndCredential formatProfileAndCredentialBody(ProfileKeyCredentialRequestContext requestContext, String body)
      throws MalformedResponseException
  {
    try {
      SignalServiceProfile signalServiceProfile = JsonUtil.fromJson(body, SignalServiceProfile.class);

      try {
        ExpiringProfileKeyCredential expiringProfileKeyCredential = signalServiceProfile.getExpiringProfileKeyCredentialResponse() != null
                                                                    ? clientZkProfileOperations.receiveExpiringProfileKeyCredential(requestContext, signalServiceProfile.getExpiringProfileKeyCredentialResponse())
                                                                    : null;
        return new ProfileAndCredential(signalServiceProfile, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL, Optional.ofNullable(expiringProfileKeyCredential));
      } catch (VerificationFailedException e) {
        Log.w(TAG, "Failed to verify credential.", e);
        return new ProfileAndCredential(signalServiceProfile, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL, Optional.empty());
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    }
  }

  public ListenableFuture<SignalServiceProfile> retrieveVersionedProfile(ACI target, ProfileKey profileKey, Optional<UnidentifiedAccess> unidentifiedAccess, Locale locale) {
    ProfileKeyVersion profileKeyIdentifier = profileKey.getProfileKeyVersion(target.getLibSignalAci());

    String                   version  = profileKeyIdentifier.serialize();
    String                   subPath  = String.format("%s/%s", target, version);
    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, subPath), "GET", null, AcceptLanguagesUtil.getHeadersWithAcceptLanguage(locale), unidentifiedAccess);

    return FutureTransformers.map(response, body -> {
      try {
        return JsonUtil.fromJson(body, SignalServiceProfile.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new MalformedResponseException("Unable to parse entity", e);
      }
    });
  }

  public void retrieveProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    try {
      downloadFromCdn(destination, 0, path, maxSizeBytes, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> writeProfile(SignalServiceProfileWrite signalServiceProfileWrite, ProfileAvatarData profileAvatar)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    String                        requestBody    = JsonUtil.toJson(signalServiceProfileWrite);
    ProfileAvatarUploadAttributes formAttributes;

    String response = makeServiceRequest(String.format(PROFILE_PATH, ""),
                                         "PUT",
                                         requestBody,
                                         NO_HEADERS,
                                         PaymentsRegionException::responseCodeHandler,
                                         Optional.empty());

    if (signalServiceProfileWrite.hasAvatar() && profileAvatar != null) {
      try {
        formAttributes = JsonUtil.fromJson(response, ProfileAvatarUploadAttributes.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new MalformedResponseException("Unable to parse entity", e);
      }

      uploadToCdn0(AVATAR_UPLOAD_PATH, formAttributes.getAcl(), formAttributes.getKey(),
                  formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                  formAttributes.getCredential(), formAttributes.getDate(),
                  formAttributes.getSignature(), profileAvatar.getData(),
                  profileAvatar.getContentType(), profileAvatar.getDataLength(),
                  profileAvatar.getOutputStreamFactory(), null, null);

       return Optional.of(formAttributes.getKey());
    }

    return Optional.empty();
  }

  public Single<ServiceResponse<IdentityCheckResponse>> performIdentityCheck(@Nonnull IdentityCheckRequest request,
                                                                             @Nonnull Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                             @Nonnull ResponseMapper<IdentityCheckResponse> responseMapper)
  {
    Single<ServiceResponse<IdentityCheckResponse>> requestSingle = Single.fromCallable(() -> {
      try (Response response = getServiceConnection(PROFILE_BATCH_CHECK_PATH, "POST", jsonRequestBody(JsonUtil.toJson(request)), Collections.emptyMap(), unidentifiedAccess, false)) {
        String body = response.body() != null ? readBodyString(response.body()): "";
        return responseMapper.map(response.code(), body, response::header, unidentifiedAccess.isPresent());
      }
    });

    return requestSingle
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .onErrorReturn(ServiceResponse::forUnknownError);
  }

  public Single<ServiceResponse<BackupAuthCheckResponse>> checkBackupAuthCredentials(@Nonnull BackupAuthCheckRequest request,
                                                                                     @Nonnull ResponseMapper<BackupAuthCheckResponse> responseMapper)
  {
    Single<ServiceResponse<BackupAuthCheckResponse>> requestSingle = Single.fromCallable(() -> {
      try (Response response = getServiceConnection(BACKUP_AUTH_CHECK, "POST", jsonRequestBody(JsonUtil.toJson(request)), Collections.emptyMap(), Optional.empty(), false)) {
        String body = response.body() != null ? readBodyString(response.body()): "";
        return responseMapper.map(response.code(), body, response::header, false);
      }
    });

    return requestSingle
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .onErrorReturn(ServiceResponse::forUnknownError);
  }

  private Single<ServiceResponse<BackupAuthCheckResponse>> createBackupAuthCheckSingle(@Nonnull String path,
                                                                                       @Nonnull BackupAuthCheckRequest request,
                                                                                       @Nonnull ResponseMapper<BackupAuthCheckResponse> responseMapper)
  {
    return Single.fromCallable(() -> {
      try (Response response = getServiceConnection(path, "POST", jsonRequestBody(JsonUtil.toJson(request)), Collections.emptyMap(), Optional.empty(), false)) {
        String body = response.body() != null ? readBodyString(response.body()): "";
        return responseMapper.map(response.code(), body, response::header, false);
      }
    });
  }

  /**
   * GET /v1/accounts/username_hash/{usernameHash}
   *
   * Gets the ACI for the given username hash, if it exists. This is an unauthenticated request.
   *
   * This network request can have the following error responses:
   * <ul>
   *   <li>404 - The username given is not associated with an account</li>
   *   <li>428 - Rate-limited, retry is available in the Retry-After header</li>
   *   <li>400 - Bad Request. The request included authentication.</li>
   * </ul>
   *
   * @param usernameHash The usernameHash to look up.
   * @return The ACI for the given username if it exists.
   * @throws IOException if a network exception occurs.
   */
  public @NonNull ACI getAciByUsernameHash(String usernameHash) throws IOException {
    String response = makeServiceRequestWithoutAuthentication(
        String.format(GET_USERNAME_PATH, URLEncoder.encode(usernameHash, StandardCharsets.UTF_8.toString())),
        "GET",
        null,
        NO_HEADERS,
        (responseCode, body) -> {
          if (responseCode == 404) {
            throw new UsernameIsNotAssociatedWithAnAccountException();
          }
        }
    );

    GetAciByUsernameResponse getAciByUsernameResponse = JsonUtil.fromJsonResponse(response, GetAciByUsernameResponse.class);
    return ACI.from(UUID.fromString(getAciByUsernameResponse.getUuid()));
  }

  /**
   * PUT /v1/accounts/username_hash/reserve
   * Reserve a username for the account. This replaces an existing reservation if one exists. The username is guaranteed to be available for 5 minutes and can
   * be confirmed with confirmUsername.
   *
   * @param usernameHashes    A list of hashed usernames encoded as web-safe base64 strings without padding. The list will have a max length of 20, and each hash will be 32 bytes.
   * @return                  The reserved username. It is available for confirmation for 5 minutes.
   * @throws IOException      Thrown when the username is invalid or taken, or when another network error occurs.
   */
  public @NonNull ReserveUsernameResponse reserveUsername(@NonNull List<String> usernameHashes) throws IOException {
    ReserveUsernameRequest reserveUsernameRequest = new ReserveUsernameRequest(usernameHashes);

    String responseString = makeServiceRequest(RESERVE_USERNAME_PATH, "PUT", JsonUtil.toJson(reserveUsernameRequest), NO_HEADERS, (responseCode, body) -> {
      switch (responseCode) {
        case 422: throw new UsernameMalformedException();
        case 409: throw new UsernameTakenException();
      }
    }, Optional.empty());

    return JsonUtil.fromJsonResponse(responseString, ReserveUsernameResponse.class);
  }

  /**
   * PUT /v1/accounts/username_hash/confirm
   * Set a previously reserved username for the account.
   *
   * @param username                The username the user wishes to confirm. For example, myusername.27
   * @param reserveUsernameResponse The response object from the reservation
   * @throws IOException            Thrown when the username is invalid or taken, or when another network error occurs.
   */
  public void confirmUsername(String username, ReserveUsernameResponse reserveUsernameResponse) throws IOException {
    try {
      byte[] randomness = new byte[32];
      random.nextBytes(randomness);

      byte[]                 proof                  = Username.generateProof(username, randomness);
      ConfirmUsernameRequest confirmUsernameRequest = new ConfirmUsernameRequest(reserveUsernameResponse.getUsernameHash(),
                                                                                 Base64UrlSafe.encodeBytesWithoutPadding(proof));

      makeServiceRequest(CONFIRM_USERNAME_PATH, "PUT", JsonUtil.toJson(confirmUsernameRequest), NO_HEADERS, (responseCode, body) -> {
        switch (responseCode) {
          case 409:
            throw new UsernameIsNotReservedException();
          case 410:
            throw new UsernameTakenException();
        }
      }, Optional.empty());
    } catch (BaseUsernameException e) {
      throw new IOException(e);
    }
  }

  /**
   * Remove the username associated with the account.
   */
  public void deleteUsername() throws IOException {
    makeServiceRequest(MODIFY_USERNAME_PATH, "DELETE", null);
  }

  /**
   * Creates a new username link for a given username.
   * @param encryptedUsername URL-safe base64-encoded encrypted username
   * @return The serverId for the generated link.
   */
  public UUID createUsernameLink(String encryptedUsername) throws IOException {
    String                      response = makeServiceRequest(USERNAME_LINK_PATH, "PUT", JsonUtil.toJson(new SetUsernameLinkRequestBody(encryptedUsername)));
    SetUsernameLinkResponseBody parsed   = JsonUtil.fromJson(response, SetUsernameLinkResponseBody.class);

    return parsed.getUsernameLinkHandle();
  }

  /** Deletes your active username link. */
  public void deleteUsernameLink() throws IOException {
    makeServiceRequest(USERNAME_LINK_PATH, "DELETE", null);
  }

  /** Given a link serverId (see {@link #createUsernameLink(String)}), this will return the encrypted username associate with the link. */
  public byte[] getEncryptedUsernameFromLinkServerId(UUID serverId) throws IOException {
    String                          response = makeServiceRequestWithoutAuthentication(String.format(USERNAME_FROM_LINK_PATH, serverId.toString()), "GET", null);
    GetUsernameFromLinkResponseBody parsed   = JsonUtil.fromJson(response, GetUsernameFromLinkResponseBody.class);

    return Base64UrlSafe.decodePaddingAgnostic(parsed.getUsernameLinkEncryptedValue());
  }

  public void deleteAccount() throws IOException {
    makeServiceRequest(DELETE_ACCOUNT_PATH, "DELETE", null);
  }

  public void requestRateLimitPushChallenge() throws IOException {
    makeServiceRequest(REQUEST_RATE_LIMIT_PUSH_CHALLENGE, "POST", "");
  }

  public void submitRateLimitPushChallenge(String challenge) throws IOException {
    String payload = JsonUtil.toJson(new SubmitPushChallengePayload(challenge));
    makeServiceRequest(SUBMIT_RATE_LIMIT_CHALLENGE, "PUT", payload);
  }

  public void submitRateLimitRecaptchaChallenge(String challenge, String recaptchaToken) throws IOException {
    String payload = JsonUtil.toJson(new SubmitRecaptchaChallengePayload(challenge, recaptchaToken));
    makeServiceRequest(SUBMIT_RATE_LIMIT_CHALLENGE, "PUT", payload);
  }

  public void redeemDonationReceipt(ReceiptCredentialPresentation receiptCredentialPresentation, boolean visible, boolean primary) throws IOException {
    String payload = JsonUtil.toJson(new RedeemReceiptRequest(Base64.encodeBytes(receiptCredentialPresentation.serialize()), visible, primary));
    makeServiceRequest(DONATION_REDEEM_RECEIPT, "POST", payload);
  }

  public StripeClientSecret createStripeOneTimePaymentIntent(String currencyCode, long amount, long level) throws IOException {
    String payload = JsonUtil.toJson(new StripeOneTimePaymentIntentPayload(amount, currencyCode, level));
    String result  = makeServiceRequestWithoutAuthentication(CREATE_STRIPE_ONE_TIME_PAYMENT_INTENT, "POST", payload);
    return JsonUtil.fromJsonResponse(result, StripeClientSecret.class);
  }

  public PayPalCreatePaymentIntentResponse createPayPalOneTimePaymentIntent(Locale locale, String currencyCode, long amount, long level, String returnUrl, String cancelUrl) throws IOException {
    Map<String, String> headers = Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry());
    String              payload = JsonUtil.toJson(new PayPalCreateOneTimePaymentIntentPayload(amount, currencyCode, level, returnUrl, cancelUrl));
    String              result  = makeServiceRequestWithoutAuthentication(CREATE_PAYPAL_ONE_TIME_PAYMENT_INTENT, "POST", payload, headers, NO_HANDLER);

    return JsonUtil.fromJsonResponse(result, PayPalCreatePaymentIntentResponse.class);
  }

  public PayPalConfirmPaymentIntentResponse confirmPayPalOneTimePaymentIntent(String currency, String amount, long level, String payerId, String paymentId, String paymentToken) throws IOException {
    String payload = JsonUtil.toJson(new PayPalConfirmOneTimePaymentIntentPayload(amount, currency, level, payerId, paymentId, paymentToken));
    Log.d(TAG, payload);
    String result  = makeServiceRequestWithoutAuthentication(CONFIRM_PAYPAL_ONE_TIME_PAYMENT_INTENT, "POST", payload);
    return JsonUtil.fromJsonResponse(result, PayPalConfirmPaymentIntentResponse.class);
  }

  public PayPalCreatePaymentMethodResponse createPayPalPaymentMethod(Locale locale, String subscriberId, String returnUrl, String cancelUrl) throws IOException {
    Map<String, String> headers = Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry());
    String              payload = JsonUtil.toJson(new PayPalCreatePaymentMethodPayload(returnUrl, cancelUrl));
    String              result  = makeServiceRequestWithoutAuthentication(String.format(CREATE_PAYPAL_SUBSCRIPTION_PAYMENT_METHOD, subscriberId), "POST", payload, headers, NO_HANDLER);
    return JsonUtil.fromJsonResponse(result, PayPalCreatePaymentMethodResponse.class);
  }

  public ReceiptCredentialResponse submitBoostReceiptCredentials(String paymentIntentId, ReceiptCredentialRequest receiptCredentialRequest, DonationProcessor processor) throws IOException {
    String payload  = JsonUtil.toJson(new BoostReceiptCredentialRequestJson(paymentIntentId, receiptCredentialRequest, processor));
    String response = makeServiceRequestWithoutAuthentication(
        BOOST_RECEIPT_CREDENTIALS,
        "POST",
        payload,
        NO_HEADERS,
        (code, body) -> {
          if (code == 204) throw new NonSuccessfulResponseCodeException(204);
        });

    ReceiptCredentialResponseJson responseJson = JsonUtil.fromJson(response, ReceiptCredentialResponseJson.class);
    if (responseJson.getReceiptCredentialResponse() != null) {
      return responseJson.getReceiptCredentialResponse();
    } else {
      throw new MalformedResponseException("Unable to parse response");
    }
  }

  /**
   * Get the DonationsConfiguration pointed at by /v1/subscriptions/configuration
   */
  public DonationsConfiguration getDonationsConfiguration(Locale locale) throws IOException {
    Map<String, String> headers = Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry());
    String              result  = makeServiceRequestWithoutAuthentication(DONATIONS_CONFIGURATION, "GET", null, headers, NO_HANDLER);

    return JsonUtil.fromJson(result, DonationsConfiguration.class);
  }

  public void updateSubscriptionLevel(String subscriberId, String level, String currencyCode, String idempotencyKey) throws IOException {
    makeServiceRequestWithoutAuthentication(String.format(UPDATE_SUBSCRIPTION_LEVEL, subscriberId, level, currencyCode, idempotencyKey), "PUT", "");
  }

  public ActiveSubscription getSubscription(String subscriberId) throws IOException {
    String response = makeServiceRequestWithoutAuthentication(String.format(SUBSCRIPTION, subscriberId), "GET", null);
    return JsonUtil.fromJson(response, ActiveSubscription.class);
  }

  public void putSubscription(String subscriberId) throws IOException {
    makeServiceRequestWithoutAuthentication(String.format(SUBSCRIPTION, subscriberId), "PUT", "");
  }

  public void deleteSubscription(String subscriberId) throws IOException {
    makeServiceRequestWithoutAuthentication(String.format(SUBSCRIPTION, subscriberId), "DELETE", null);
  }

  public StripeClientSecret createStripeSubscriptionPaymentMethod(String subscriberId) throws IOException {
    String response = makeServiceRequestWithoutAuthentication(String.format(CREATE_STRIPE_SUBSCRIPTION_PAYMENT_METHOD, subscriberId), "POST", "");
    return JsonUtil.fromJson(response, StripeClientSecret.class);
  }

  public void setDefaultStripeSubscriptionPaymentMethod(String subscriberId, String paymentMethodId) throws IOException {
    makeServiceRequestWithoutAuthentication(String.format(DEFAULT_STRIPE_SUBSCRIPTION_PAYMENT_METHOD, subscriberId, paymentMethodId), "POST", "");
  }

  public void setDefaultPaypalSubscriptionPaymentMethod(String subscriberId, String paymentMethodId) throws IOException {
    makeServiceRequestWithoutAuthentication(String.format(DEFAULT_PAYPAL_SUBSCRIPTION_PAYMENT_METHOD, subscriberId, paymentMethodId), "POST", "");
  }

  public ReceiptCredentialResponse submitReceiptCredentials(String subscriptionId, ReceiptCredentialRequest receiptCredentialRequest) throws IOException {
    String payload  = JsonUtil.toJson(new ReceiptCredentialRequestJson(receiptCredentialRequest));
    String response = makeServiceRequestWithoutAuthentication(
        String.format(SUBSCRIPTION_RECEIPT_CREDENTIALS, subscriptionId),
        "POST",
        payload,
        NO_HEADERS,
        (code, body) -> {
          if (code == 204) throw new NonSuccessfulResponseCodeException(204);
        });

    ReceiptCredentialResponseJson responseJson = JsonUtil.fromJson(response, ReceiptCredentialResponseJson.class);
    if (responseJson.getReceiptCredentialResponse() != null) {
      return responseJson.getReceiptCredentialResponse();
    } else {
      throw new MalformedResponseException("Unable to parse response");
    }
  }

  public CreateCallLinkCredentialResponse getCallLinkAuthResponse(CreateCallLinkCredentialRequest request) throws IOException {
    String payload = JsonUtil.toJson(CreateCallLinkAuthRequest.create(request));
    String response = makeServiceRequest(
        CALL_LINK_CREATION_AUTH,
        "POST",
        payload
    );

    return JsonUtil.fromJson(response, CreateCallLinkAuthResponse.class).getCreateCallLinkCredentialResponse();
  }

  private AuthCredentials getAuthCredentials(String authPath) throws IOException {
    String              response = makeServiceRequest(authPath, "GET", null);
    AuthCredentials     token    = JsonUtil.fromJson(response, AuthCredentials.class);
    return token;
  }

  private String getCredentials(String authPath) throws IOException {
    return getAuthCredentials(authPath).asBasic();
  }

  public String getContactDiscoveryAuthorization() throws IOException {
    return getCredentials(DIRECTORY_AUTH_PATH);
  }

  public AuthCredentials getKeyBackupServiceAuthorization() throws IOException {
    return getAuthCredentials(KBS_AUTH_PATH);
  }

  public AuthCredentials getPaymentsAuthorization() throws IOException {
    return getAuthCredentials(PAYMENTS_AUTH_PATH);
  }

  public TokenResponse getKeyBackupServiceToken(String authorizationToken, String enclaveName)
      throws IOException
  {
    try (Response response = makeRequest(ClientSet.KeyBackup, authorizationToken, null, "/v1/token/" + enclaveName, "GET", null)) {
      return readBodyJson(response, TokenResponse.class);
    }
  }

  public KeyBackupResponse putKbsData(String authorizationToken, KeyBackupRequest request, List<String> cookies, String mrenclave)
      throws IOException
  {
    try (Response response = makeRequest(ClientSet.KeyBackup, authorizationToken, cookies, "/v1/backup/" + mrenclave, "PUT", JsonUtil.toJson(request))) {
      return readBodyJson(response, KeyBackupResponse.class);
    }
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    String response = makeServiceRequest(TURN_SERVER_INFO, "GET", null);
    return JsonUtil.fromJson(response, TurnServerInfo.class);
  }

  public String getStorageAuth() throws IOException {
    String              response     = makeServiceRequest("/v1/storage/auth", "GET", null);
    StorageAuthResponse authResponse = JsonUtil.fromJson(response, StorageAuthResponse.class);

    return Credentials.basic(authResponse.getUsername(), authResponse.getPassword());
  }

  public StorageManifest getStorageManifest(String authToken) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/manifest", "GET", null, NO_HANDLER)) {
      return StorageManifest.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public StorageManifest getStorageManifestIfDifferentVersion(String authToken, long version) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/manifest/version/" + version, "GET", null, NO_HANDLER)) {
      return StorageManifest.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public StorageItems readStorageItems(String authToken, ReadOperation operation) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/read", "PUT", protobufRequestBody(operation), NO_HANDLER)) {
      return StorageItems.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public Optional<StorageManifest> writeStorageContacts(String authToken, WriteOperation writeOperation) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage", "PUT", protobufRequestBody(writeOperation), NO_HANDLER)) {
      return Optional.empty();
    } catch (ContactManifestMismatchException e) {
      return Optional.of(StorageManifest.ADAPTER.decode(e.getResponseBody()));
    }
  }

  public void pingStorageService() throws IOException {
    try (Response response = makeStorageRequest(null, "/ping", "GET", null, NO_HANDLER)) {
      return;
    }
  }

  public RemoteConfigResponse getRemoteConfig() throws IOException {
    String response = makeServiceRequest("/v1/config", "GET", null);
    return JsonUtil.fromJson(response, RemoteConfigResponse.class);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.soTimeoutMillis = soTimeoutMillis;
  }

  public void cancelInFlightRequests() {
    synchronized (connections) {
      Log.w(TAG, "Canceling: " + connections.size());
      for (Call connection : connections) {
        Log.w(TAG, "Canceling: " + connection);
        connection.cancel();
      }
    }
  }

  public AttachmentV2UploadAttributes getAttachmentV2UploadAttributes()
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    String response = makeServiceRequest(ATTACHMENT_V2_PATH, "GET", null);
    try {
      return JsonUtil.fromJson(response, AttachmentV2UploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    }
  }

  public AttachmentV4UploadAttributes getAttachmentV4UploadAttributes()
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    String response = makeServiceRequest(ATTACHMENT_V4_PATH, "GET", null);
    try {
      return JsonUtil.fromJson(response, AttachmentV4UploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    }
  }

  public AttachmentDigest uploadGroupV2Avatar(byte[] avatarCipherText, AvatarUploadAttributes uploadAttributes)
      throws IOException
  {
    return uploadToCdn0(AVATAR_UPLOAD_PATH, uploadAttributes.acl, uploadAttributes.key,
                       uploadAttributes.policy, uploadAttributes.algorithm,
                       uploadAttributes.credential, uploadAttributes.date,
                       uploadAttributes.signature,
                       new ByteArrayInputStream(avatarCipherText),
                       "application/octet-stream", avatarCipherText.length,
                       new NoCipherOutputStreamFactory(),
                       null, null);
  }

  public Pair<Long, AttachmentDigest> uploadAttachment(PushAttachmentData attachment, AttachmentV2UploadAttributes uploadAttributes)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    long             id     = Long.parseLong(uploadAttributes.getAttachmentId());
    AttachmentDigest digest = uploadToCdn0(ATTACHMENT_UPLOAD_PATH, uploadAttributes.getAcl(), uploadAttributes.getKey(),
                                           uploadAttributes.getPolicy(), uploadAttributes.getAlgorithm(),
                                           uploadAttributes.getCredential(), uploadAttributes.getDate(),
                                           uploadAttributes.getSignature(), attachment.getData(),
                                           "application/octet-stream", attachment.getDataSize(),
                                           attachment.getOutputStreamFactory(), attachment.getListener(),
                                           attachment.getCancelationSignal());

    return new Pair<>(id, digest);
  }

  public ResumableUploadSpec getResumableUploadSpec(AttachmentV4UploadAttributes uploadAttributes) throws IOException {
    return new ResumableUploadSpec(Util.getSecretBytes(64),
                                   Util.getSecretBytes(16),
                                   uploadAttributes.getKey(),
                                   uploadAttributes.getCdn(),
                                   getResumableUploadUrl(uploadAttributes.getCdn(), uploadAttributes.getSignedUploadLocation(), uploadAttributes.getHeaders()),
                                   System.currentTimeMillis() + CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
                                   uploadAttributes.getHeaders());
  }

  public AttachmentDigest uploadAttachment(PushAttachmentData attachment) throws IOException {

    if (attachment.getResumableUploadSpec() == null || attachment.getResumableUploadSpec().getExpirationTimestamp() < System.currentTimeMillis()) {
      throw new ResumeLocationInvalidException();
    }

    if (attachment.getResumableUploadSpec().getCdnNumber() == 2) {
      return uploadToCdn2(attachment.getResumableUploadSpec().getResumeLocation(),
                          attachment.getData(),
                          "application/octet-stream",
                          attachment.getDataSize(),
                          attachment.getOutputStreamFactory(),
                          attachment.getListener(),
                          attachment.getCancelationSignal());
    } else {
      return uploadToCdn3(attachment.getResumableUploadSpec().getResumeLocation(),
                          attachment.getData(),
                          "application/offset+octet-stream",
                          attachment.getDataSize(),
                          attachment.getOutputStreamFactory(),
                          attachment.getListener(),
                          attachment.getCancelationSignal(),
                          attachment.getResumableUploadSpec().getHeaders());
    }
  }

  private void downloadFromCdn(File destination, int cdnNumber, String path, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    try (FileOutputStream outputStream = new FileOutputStream(destination, true)) {
      downloadFromCdn(outputStream, destination.length(), cdnNumber, path, maxSizeBytes, listener);
    }
  }

  private void downloadFromCdn(OutputStream outputStream, long offset, int cdnNumber, String path, long maxSizeBytes, ProgressListener listener)
      throws PushNetworkException, NonSuccessfulResponseCodeException, MissingConfigurationException {
    ConnectionHolder[] cdnNumberClients = cdnClientsMap.get(cdnNumber);
    if (cdnNumberClients == null) {
      throw new MissingConfigurationException("Attempted to download from unsupported CDN number: " + cdnNumber + ", Our configuration supports: " + cdnClientsMap.keySet());
    }
    ConnectionHolder   connectionHolder = getRandom(cdnNumberClients, random);
    OkHttpClient       okHttpClient     = connectionHolder.getClient()
                                                          .newBuilder()
                                                          .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                          .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                          .build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + "/" + path).get();

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (offset > 0) {
      Log.i(TAG, "Starting download from CDN with offset " + offset);
      request.addHeader("Range", "bytes=" + offset + "-");
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ResponseBody body = response.body();

        if (body == null)                        throw new PushNetworkException("No response body!");
        if (body.contentLength() > maxSizeBytes) throw new PushNetworkException("Response exceeds max size!");

        InputStream  in     = body.byteStream();
        byte[]       buffer = new byte[32768];

        int  read      = 0;
        long totalRead = offset;

        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
          outputStream.write(buffer, 0, read);
          if ((totalRead += read) > maxSizeBytes) throw new PushNetworkException("Response exceeded max size!");

          if (listener != null) {
            listener.onAttachmentProgress(body.contentLength() + offset, totalRead);
          }
        }
      } else if (response.code() == 416) {
        throw new RangeException(offset);
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (NonSuccessfulResponseCodeException | PushNetworkException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private AttachmentDigest uploadToCdn0(String path, String acl, String key, String policy, String algorithm,
                                        String credential, String date, String signature,
                                        InputStream data, String contentType, long length,
                                        OutputStreamFactory outputStreamFactory, ProgressListener progressListener,
                                        CancelationSignal cancelationSignal)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(0), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    DigestingRequestBody file = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal, 0);

    RequestBody requestBody = new MultipartBody.Builder()
                                               .setType(MultipartBody.FORM)
                                               .addFormDataPart("acl", acl)
                                               .addFormDataPart("key", key)
                                               .addFormDataPart("policy", policy)
                                               .addFormDataPart("Content-Type", contentType)
                                               .addFormDataPart("x-amz-algorithm", algorithm)
                                               .addFormDataPart("x-amz-credential", credential)
                                               .addFormDataPart("x-amz-date", date)
                                               .addFormDataPart("x-amz-signature", signature)
                                               .addFormDataPart("file", "file", file)
                                               .build();

    Request.Builder request = new Request.Builder()
                                         .url(connectionHolder.getUrl() + "/" + path)
                                         .post(requestBody);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) return file.getAttachmentDigest();
      else                         throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private String getResumableUploadUrl(int cdn, String signedUrl, Map<String, String> headers) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(cdn), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, signedUrl))
                                                   .post(RequestBody.create(null, ""));

    for (Map.Entry<String, String> header : headers.entrySet()) {
      if (!header.getKey().equalsIgnoreCase("host")) {
        request.header(header.getKey(), header.getValue());
      }
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    request.addHeader("Content-Length", "0");

    if (cdn == 2) {
      request.addHeader("Content-Type", "application/octet-stream");
    } else if (cdn == 3) {
      request.addHeader("Upload-Defer-Length", "1")
             .addHeader("Tus-Resumable", "1.0.0");
    } else {
      throw new AssertionError("Unknown CDN version: " + cdn);
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        return response.header("location");
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private AttachmentDigest uploadToCdn2(String resumableUrl, InputStream data, String contentType, long length, OutputStreamFactory outputStreamFactory, ProgressListener progressListener, CancelationSignal cancelationSignal) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    ResumeInfo           resumeInfo = getResumeInfoCdn2(resumableUrl, length);
    DigestingRequestBody file       = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal, resumeInfo.contentStart);

    if (resumeInfo.contentStart == length) {
      Log.w(TAG, "Resume start point == content length");
      try (NowhereBufferedSink buffer = new NowhereBufferedSink()) {
        file.writeTo(buffer);
      }
      return file.getAttachmentDigest();
    }

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .put(file)
                                                   .addHeader("Content-Range", resumeInfo.contentRange);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) return file.getAttachmentDigest();
      else                         throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private AttachmentDigest uploadToCdn3(String resumableUrl,
                                        InputStream data,
                                        String contentType,
                                        long length,
                                        OutputStreamFactory outputStreamFactory,
                                        ProgressListener progressListener,
                                        CancelationSignal cancelationSignal,
                                        Map<String, String> headers)
      throws IOException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(3), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    ResumeInfo           resumeInfo = getResumeInfoCdn3(resumableUrl, headers);
    DigestingRequestBody file       = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal, resumeInfo.contentStart);

    if (resumeInfo.contentStart == length) {
      Log.w(TAG, "Resume start point == content length");
      try (NowhereBufferedSink buffer = new NowhereBufferedSink()) {
        file.writeTo(buffer);
      }
      return file.getAttachmentDigest();
    } else if (resumeInfo.contentStart != 0) {
      Log.w(TAG, "Resuming previous attachment upload");
    }

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .patch(file)
                                                   .addHeader("Upload-Offset", String.valueOf(resumeInfo.contentStart))
                                                   .addHeader("Upload-Length", String.valueOf(length))
                                                   .addHeader("Tus-Resumable", "1.0.0");

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        return file.getAttachmentDigest();
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private ResumeInfo getResumeInfoCdn2(String resumableUrl, long contentLength) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    final long   offset;
    final String contentRange;

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .put(RequestBody.create(null, ""))
                                                   .addHeader("Content-Range", String.format(Locale.US, "bytes */%d", contentLength));

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        offset       = contentLength;
        contentRange = null;
      } else if (response.code() == 308) {
        String rangeCompleted = response.header("Range");

        if (rangeCompleted == null) {
          offset = 0;
        } else {
          offset = Long.parseLong(rangeCompleted.split("-")[1]) + 1;
        }

        contentRange = String.format(Locale.US, "bytes %d-%d/%d", offset, contentLength - 1, contentLength);
      } else if (response.code() == 404) {
        throw new ResumeLocationInvalidException();
      } else {
        throw new NonSuccessfulResumableUploadResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    return new ResumeInfo(contentRange, offset);
  }

  private ResumeInfo getResumeInfoCdn3(String resumableUrl, Map<String, String> headers) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(3), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    final long   offset;

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .head()
                                                   .addHeader("Tus-Resumable", "1.0.0");

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        offset = Long.parseLong(response.header("Upload-Offset"));
      } else if (response.code() >= 400 || response.code() < 500) {
        throw new ResumeLocationInvalidException("Response: " + response);
      } else {
        throw new NonSuccessfulResumableUploadResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    return new ResumeInfo(null, offset);
  }

  private static HttpUrl buildConfiguredUrl(ConnectionHolder connectionHolder, String url) throws IOException {
    final HttpUrl endpointUrl = HttpUrl.get(connectionHolder.url);
    final HttpUrl resumableHttpUrl;
    try {
      resumableHttpUrl = HttpUrl.get(url);
    } catch (IllegalArgumentException e) {
      throw new IOException("Malformed URL!", e);
    }

    return new HttpUrl.Builder().scheme(endpointUrl.scheme())
                                .host(endpointUrl.host())
                                .port(endpointUrl.port())
                                .encodedPath(endpointUrl.encodedPath())
                                .addEncodedPathSegments(resumableHttpUrl.encodedPath().substring(1))
                                .encodedQuery(resumableHttpUrl.encodedQuery())
                                .encodedFragment(resumableHttpUrl.encodedFragment())
                                .build();
  }

  private String makeServiceRequestWithoutAuthentication(String urlFragment, String method, String jsonBody)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    return makeServiceRequestWithoutAuthentication(urlFragment, method, jsonBody, NO_HEADERS, NO_HANDLER);
  }

  private String makeServiceRequestWithoutAuthentication(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    try (Response response = makeServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, responseCodeHandler, Optional.empty(), true)) {
      return readBodyString(response);
    }
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, NO_HEADERS, NO_HANDLER, Optional.empty());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    try (Response response = makeServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, responseCodeHandler, unidentifiedAccessKey, false)) {
      return readBodyString(response);
    }
  }

  private static RequestBody jsonRequestBody(String jsonBody) {
    return jsonBody != null ? RequestBody.create(MediaType.parse("application/json"), jsonBody)
                            : null;
  }

  private static RequestBody protobufRequestBody(Message<?, ?> protobufBody) {
    return protobufBody != null ? RequestBody.create(MediaType.parse("application/x-protobuf"), protobufBody.encode())
                                : null;
  }

  private ListenableFuture<String> submitServiceRequest(String urlFragment,
                                                        String method,
                                                        String jsonBody,
                                                        Map<String, String> headers,
                                                        Optional<UnidentifiedAccess> unidentifiedAccessKey)
  {
    OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccessKey.isPresent());
    Call         call         = okHttpClient.newCall(buildServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, unidentifiedAccessKey, false));

    synchronized (connections) {
      connections.add(call);
    }

    SettableFuture<String> bodyFuture = new SettableFuture<>();

    call.enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) {
        try (ResponseBody body = response.body()) {
          validateServiceResponse(response);
          bodyFuture.set(readBodyString(body));
        } catch (IOException e) {
          bodyFuture.setException(e);
        }
      }

      @Override
      public void onFailure(Call call, IOException e) {
        bodyFuture.setException(e);
      }
    });

    return bodyFuture;
  }

  private Response makeServiceRequest(String urlFragment,
                                      String method,
                                      RequestBody body,
                                      Map<String, String> headers,
                                      ResponseCodeHandler responseCodeHandler,
                                      Optional<UnidentifiedAccess> unidentifiedAccessKey,
                                      boolean doNotAddAuthenticationOrUnidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    Response response = getServiceConnection(urlFragment, method, body, headers, unidentifiedAccessKey, doNotAddAuthenticationOrUnidentifiedAccessKey);
    responseCodeHandler.handle(response.code(), response.body());
    return validateServiceResponse(response);
  }

  private Response validateServiceResponse(Response response)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException {
    int    responseCode    = response.code();
    String responseMessage = response.message();

    switch (responseCode) {
      case 413:
      case 429: {
        long           retryAfterLong = Util.parseLong(response.header("Retry-After"), -1);
        Optional<Long> retryAfter     = retryAfterLong != -1 ? Optional.of(TimeUnit.SECONDS.toMillis(retryAfterLong)) : Optional.empty();
        throw new RateLimitException(responseCode, "Rate limit exceeded: " + responseCode, retryAfter);
      }
      case 401:
      case 403:
        throw new AuthorizationFailedException(responseCode, "Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        MismatchedDevices mismatchedDevices = readResponseJson(response, MismatchedDevices.class);

        throw new MismatchedDevicesException(mismatchedDevices);
      case 410:
        StaleDevices staleDevices = readResponseJson(response, StaleDevices.class);

        throw new StaleDevicesException(staleDevices);
      case 411:
        DeviceLimit deviceLimit = readResponseJson(response, DeviceLimit.class);

        throw new DeviceLimitExceededException(deviceLimit);
      case 417:
        throw new ExpectationFailedException();
      case 423:
        RegistrationLockFailure accountLockFailure = readResponseJson(response, RegistrationLockFailure.class);

        throw new LockedException(accountLockFailure.length,
                                  accountLockFailure.timeRemaining,
                                  accountLockFailure.svr1Credentials,
                                  accountLockFailure.svr2Credentials);
      case 428:
        ProofRequiredResponse proofRequiredResponse = readResponseJson(response, ProofRequiredResponse.class);
        String                retryAfterRaw = response.header("Retry-After");
        long                  retryAfter    = Util.parseInt(retryAfterRaw, -1);

        throw new ProofRequiredException(proofRequiredResponse, retryAfter);

      case 499:
        throw new DeprecatedVersionException();

      case 508:
        throw new ServerRejectedException();
    }

    if (responseCode != 200 && responseCode != 202 && responseCode != 204) {
      throw new NonSuccessfulResponseCodeException(responseCode, "Bad response: " + responseCode + " " + responseMessage);
    }

    return response;
  }

  private Response getServiceConnection(String urlFragment,
                                        String method,
                                        RequestBody body,
                                        Map<String, String> headers,
                                        Optional<UnidentifiedAccess> unidentifiedAccess,
                                        boolean doNotAddAuthenticationOrUnidentifiedAccessKey)
      throws PushNetworkException
  {
    try {
      OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccess.isPresent());
      Call         call         = okHttpClient.newCall(buildServiceRequest(urlFragment, method, body, headers, unidentifiedAccess, doNotAddAuthenticationOrUnidentifiedAccessKey));

      synchronized (connections) {
        connections.add(call);
      }

      try {
        return call.execute();
      } finally {
        synchronized (connections) {
          connections.remove(call);
        }
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private OkHttpClient buildOkHttpClient(boolean unidentified) {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);
    OkHttpClient            baseClient       = unidentified ? connectionHolder.getUnidentifiedClient() : connectionHolder.getClient();

    return baseClient.newBuilder()
                     .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                     .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                     .retryOnConnectionFailure(automaticNetworkRetry)
                     .build();
  }

  private Request buildServiceRequest(String urlFragment,
                                      String method,
                                      RequestBody body,
                                      Map<String, String> headers,
                                      Optional<UnidentifiedAccess> unidentifiedAccess,
                                      boolean doNotAddAuthenticationOrUnidentifiedAccessKey) {

    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);

//      Log.d(TAG, "Push service URL: " + connectionHolder.getUrl());
//      Log.d(TAG, "Opening URL: " + String.format("%s%s", connectionHolder.getUrl(), urlFragment));

    Request.Builder request = new Request.Builder();
    request.url(String.format("%s%s", connectionHolder.getUrl(), urlFragment));
    request.method(method, body);

    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    if (!headers.containsKey("Authorization") && !doNotAddAuthenticationOrUnidentifiedAccessKey) {
      if (unidentifiedAccess.isPresent()) {
        request.addHeader("Unidentified-Access-Key", Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
      } else if (credentialsProvider.getPassword() != null) {
        request.addHeader("Authorization", getAuthorizationHeader(credentialsProvider));
      }
    }

    if (signalAgent != null) {
      request.addHeader("X-Signal-Agent", signalAgent);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    return request.build();
  }


  private ConnectionHolder[] clientsFor(ClientSet clientSet) {
    switch (clientSet) {
      case KeyBackup:
        return keyBackupServiceClients;
      default:
        throw new AssertionError("Unknown attestation purpose");
    }
  }

  Response makeRequest(ClientSet clientSet, String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(clientsFor(clientSet), random);

    OkHttpClient okHttpClient = connectionHolder.getClient()
                                                .newBuilder()
                                                .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                .build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);

    if (body != null) {
      request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
    } else {
      request.method(method, null);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    if (cookies != null && !cookies.isEmpty()) {
      request.addHeader("Cookie", Util.join(cookies, "; "));
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful()) {
        return response;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    switch (response.code()) {
      case 401:
      case 403:
        throw new AuthorizationFailedException(response.code(), "Authorization failed!");
      case 409:
        throw new RemoteAttestationResponseExpiredException("Remote attestation response expired");
      case 429:
        throw new RateLimitException(response.code(), "Rate limit exceeded: " + response.code());
    }

    throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
  }

  private Response makeStorageRequest(String authorization, String path, String method, RequestBody body, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(storageClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

//    Log.d(TAG, "Opening URL: " + connectionHolder.getUrl());

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);
    request.method(method, body);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful() && response.code() != 204) {
        return response;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    try (ResponseBody responseBody = response.body()) {
      responseCodeHandler.handle(response.code(), responseBody, response::header);

      switch (response.code()) {
        case 204:
          throw new NoContentException("No content!");
        case 401:
        case 403:
          throw new AuthorizationFailedException(response.code(), "Authorization failed!");
        case 404:
          throw new NotFoundException("Not found");
        case 409:
          if (responseBody != null) {
            throw new ContactManifestMismatchException(readBodyBytes(responseBody));
          } else {
            throw new ConflictException();
          }
        case 429:
          throw new RateLimitException(response.code(), "Rate limit exceeded: " + response.code());
        case 499:
          throw new DeprecatedVersionException();
      }

      throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    }
  }

  public CallingResponse makeCallingRequest(long requestId, String url, String httpMethod, List<Pair<String, String>> headers, byte[] body) {
    ConnectionHolder connectionHolder = getRandom(serviceClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .followRedirects(false)
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    RequestBody     requestBody = body != null ? RequestBody.create(null, body) : null;
    Request.Builder builder     = new Request.Builder()
                                             .url(url)
                                             .method(httpMethod, requestBody);

    if (headers != null) {
      for (Pair<String, String> header : headers) {
        builder.addHeader(header.first(), header.second());
      }
    }

    Request request = builder.build();

    for (int i = 0; i < MAX_FOLLOW_UPS; i++) {
      try (Response response = okHttpClient.newCall(request).execute()) {
        int responseStatus = response.code();

        if (responseStatus != 307) {
          return new CallingResponse.Success(requestId,
                                             responseStatus,
                                             response.body() != null ? response.body().bytes() : new byte[0]);
        }

        String  location = response.header("Location");
        HttpUrl newUrl   = location != null ? request.url().resolve(location) : null;

        if (newUrl != null) {
          request = request.newBuilder().url(newUrl).build();
        } else {
          return new CallingResponse.Error(requestId, new IOException("Received redirect without a valid Location header"));
        }
      } catch (IOException e) {
        Log.w(TAG, "Exception during ringrtc http call.", e);
        return new CallingResponse.Error(requestId, e);
      }
    }

    Log.w(TAG, "Calling request max redirects exceeded");
    return new CallingResponse.Error(requestId, new IOException("Redirect limit exceeded"));
  }

  private ServiceConnectionHolder[] createServiceConnectionHolders(SignalUrl[] urls,
                                                                   List<Interceptor> interceptors,
                                                                   SocketFactory socketFactory,
                                                                   ProxySelector proxySelector,
                                                                   Dns dns)
  {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(new ServiceConnectionHolder(createConnectionClient(url, interceptors, socketFactory, proxySelector, dns),
                                                               createConnectionClient(url, interceptors, socketFactory, proxySelector, dns),
                                                               url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private static Map<Integer, ConnectionHolder[]> createCdnClientsMap(final Map<Integer, SignalCdnUrl[]> signalCdnUrlMap,
                                                                      final List<Interceptor> interceptors,
                                                                      final SocketFactory socketFactory,
                                                                      final ProxySelector proxySelector,
                                                                      final Dns dns) {
    validateConfiguration(signalCdnUrlMap);
    final Map<Integer, ConnectionHolder[]> result = new HashMap<>();
    for (Map.Entry<Integer, SignalCdnUrl[]> entry : signalCdnUrlMap.entrySet()) {
      result.put(entry.getKey(),
                 createConnectionHolders(entry.getValue(), interceptors, socketFactory, proxySelector, dns));
    }
    return Collections.unmodifiableMap(result);
  }

  private static void validateConfiguration(Map<Integer, SignalCdnUrl[]> signalCdnUrlMap) {
    if (!signalCdnUrlMap.containsKey(0) || !signalCdnUrlMap.containsKey(2)) {
      throw new AssertionError("Configuration used to create PushServiceSocket must support CDN 0 and CDN 2");
    }
  }

  private static ConnectionHolder[] createConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors, SocketFactory socketFactory, ProxySelector proxySelector, Dns dns) {
    List<ConnectionHolder> connectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url, interceptors, socketFactory, proxySelector, dns), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private static OkHttpClient createConnectionClient(SignalUrl url, List<Interceptor> interceptors, SocketFactory socketFactory, ProxySelector proxySelector, Dns dns) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                                     .socketFactory(socketFactory)
                                                     .proxySelector(proxySelector)
                                                     .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                                                     .connectionSpecs(url.getConnectionSpecs().orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                                                     .dns(dns);

      builder.sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
             .connectionSpecs(url.getConnectionSpecs().orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
             .build();

      builder.connectionPool(new ConnectionPool(5, 45, TimeUnit.SECONDS));

      for (Interceptor interceptor : interceptors) {
        builder.addInterceptor(interceptor);
      }

      return builder.build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
    try {
      String identifier = credentialsProvider.getAci() != null ? credentialsProvider.getAci().toString() : credentialsProvider.getE164();
      if (credentialsProvider.getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
        identifier += "." + credentialsProvider.getDeviceId();
      }
      return "Basic " + Base64.encodeBytes((identifier + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private ConnectionHolder getRandom(ConnectionHolder[] connections, SecureRandom random) {
    return connections[random.nextInt(connections.length)];
  }

  /**
   * Converts {@link IOException} on body byte reading to {@link PushNetworkException}.
   */
  private static byte[] readBodyBytes(Response response) throws PushNetworkException, MalformedResponseException {
    if (response.body() == null) {
      throw new MalformedResponseException("No body!");
    }

    try {
      return response.body().bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private static byte[] readBodyBytes(@Nonnull ResponseBody responseBody) throws PushNetworkException {
    try {
      return responseBody.bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   */
  static String readBodyString(Response response) throws PushNetworkException, MalformedResponseException {
    return readBodyString(response.body());
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   */
  private static String readBodyString(ResponseBody body) throws PushNetworkException, MalformedResponseException {
    if (body == null) {
      throw new MalformedResponseException("No body!");
    }

    try {
      return body.string();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link MalformedResponseException}
   */
  private static <T> T readBodyJson(Response response, Class<T> clazz) throws PushNetworkException, MalformedResponseException {
    return readBodyJson(response.body(), clazz);
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link MalformedResponseException}
   */
  private static <T> T readBodyJson(ResponseBody body, Class<T> clazz) throws PushNetworkException, MalformedResponseException {
    String json = readBodyString(body);

    try {
      return JsonUtil.fromJson(json, clazz);
    } catch (JsonProcessingException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link NonSuccessfulResponseCodeException} with response code detail.
   */
  private static <T> T readResponseJson(Response response, Class<T> clazz)
      throws PushNetworkException, MalformedResponseException
  {
      return readBodyJson(response.body(), clazz);
  }

  public enum VerificationCodeTransport { SMS, VOICE }

  private static class RegistrationLock {
    @JsonProperty
    private String pin;

    public RegistrationLock() {}

    public RegistrationLock(String pin) {
      this.pin = pin;
    }
  }

  private static class RegistrationLockV2 {
    @JsonProperty
    private String registrationLock;

    public RegistrationLockV2() {}

    public RegistrationLockV2(String registrationLock) {
      this.registrationLock = registrationLock;
    }
  }

  public static class RegistrationLockFailure {
    @JsonProperty
    public int length;

    @JsonProperty
    public long timeRemaining;

    @JsonProperty("backupCredentials")
    public AuthCredentials svr1Credentials;

    @JsonProperty
    public AuthCredentials svr2Credentials;
  }

  private static class ConnectionHolder {

    private final OkHttpClient     client;
    private final String           url;
    private final Optional<String> hostHeader;

    private ConnectionHolder(OkHttpClient client, String url, Optional<String> hostHeader) {
      this.client     = client;
      this.url        = url;
      this.hostHeader = hostHeader;
    }

    OkHttpClient getClient() {
      return client;
    }

    public String getUrl() {
      return url;
    }

    Optional<String> getHostHeader() {
      return hostHeader;
    }
  }

  private static class ServiceConnectionHolder extends ConnectionHolder {

    private final OkHttpClient unidentifiedClient;

    private ServiceConnectionHolder(OkHttpClient identifiedClient, OkHttpClient unidentifiedClient, String url, Optional<String> hostHeader) {
      super(identifiedClient, url, hostHeader);
      this.unidentifiedClient = unidentifiedClient;
    }

    OkHttpClient getUnidentifiedClient() {
      return unidentifiedClient;
    }
  }

  private interface ResponseCodeHandler {
    void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException, PushNetworkException;

    default void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {
      handle(responseCode, body);
    }
  }

  private static class EmptyResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body) { }
  }

  public enum ClientSet { KeyBackup }

  public CredentialResponse retrieveGroupsV2Credentials(long todaySeconds)
      throws IOException
  {
    long todayPlus7 = todaySeconds + TimeUnit.DAYS.toSeconds(7);
    String response = makeServiceRequest(String.format(Locale.US, GROUPSV2_CREDENTIAL, todaySeconds, todayPlus7),
                                         "GET",
                                         null,
                                         NO_HEADERS,
                                         NO_HANDLER,
                                         Optional.empty());

    return JsonUtil.fromJson(response, CredentialResponse.class);
  }

  private static final ResponseCodeHandler GROUPS_V2_PUT_RESPONSE_HANDLER   = (responseCode, body) -> {
    if (responseCode == 409) throw new GroupExistsException();
  };;
  private static final ResponseCodeHandler GROUPS_V2_GET_LOGS_HANDLER       = NO_HANDLER;
  private static final ResponseCodeHandler GROUPS_V2_GET_CURRENT_HANDLER    = (responseCode, body) -> {
    switch (responseCode) {
      case 403: throw new NotInGroupException();
      case 404: throw new GroupNotFoundException();
    }
  };
  private static final ResponseCodeHandler GROUPS_V2_PATCH_RESPONSE_HANDLER = (responseCode, body) -> {
    if (responseCode == 400) throw new GroupPatchNotAcceptedException();
  };
  private static final ResponseCodeHandler GROUPS_V2_GET_JOIN_INFO_HANDLER  = new ResponseCodeHandler() {
    @Override
    public void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException {
      if (responseCode == 403) throw new ForbiddenException();
    }

    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException {
      if (responseCode == 403) {
        throw new ForbiddenException(Optional.ofNullable(getHeader.apply("X-Signal-Forbidden-Reason")));
      }
    }
  };

  public void putNewGroupsV2Group(Group group, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_GROUP,
                                                "PUT",
                                                protobufRequestBody(group),
                                                GROUPS_V2_PUT_RESPONSE_HANDLER))
    {
      return;
    }
  }

  public Group getGroupsV2Group(GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_GROUP,
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_CURRENT_HANDLER))
    {
      return Group.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public AvatarUploadAttributes getGroupsV2AvatarUploadForm(String authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization,
                                                GROUPSV2_AVATAR_REQUEST,
                                                "GET",
                                                null,
                                                NO_HANDLER))
    {
      return AvatarUploadAttributes.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupChange patchGroupsV2Group(GroupChange.Actions groupChange, String authorization, Optional<byte[]> groupLinkPassword)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    String path;

    if (groupLinkPassword.isPresent()) {
      path = String.format(GROUPSV2_GROUP_PASSWORD, Base64UrlSafe.encodeBytesWithoutPadding(groupLinkPassword.get()));
    } else {
      path = GROUPSV2_GROUP;
    }

    try (Response response = makeStorageRequest(authorization,
                                                path,
                                                "PATCH",
                                                protobufRequestBody(groupChange),
                                                GROUPS_V2_PATCH_RESPONSE_HANDLER))
    {
      return GroupChange.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupHistory getGroupsV2GroupHistory(int fromVersion, GroupsV2AuthorizationString authorization, int highestKnownEpoch, boolean includeFirstState)
      throws IOException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                String.format(Locale.US, GROUPSV2_GROUP_CHANGES, fromVersion, highestKnownEpoch, includeFirstState),
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_LOGS_HANDLER))
    {

      if (response.body() == null) {
        throw new PushNetworkException("No body!");
      }

      GroupChanges groupChanges;
      try (InputStream input = response.body().byteStream()) {
        groupChanges = GroupChanges.ADAPTER.decode(input);
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      if (response.code() == 206) {
        String                 contentRangeHeader = response.header("Content-Range");
        Optional<ContentRange> contentRange       = ContentRange.parse(contentRangeHeader);

        if (contentRange.isPresent()) {
          Log.i(TAG, "Additional logs for group: " + contentRangeHeader);
          return new GroupHistory(groupChanges, contentRange);
        } else {
          Log.w(TAG, "Unable to parse Content-Range header: " + contentRangeHeader);
          throw new MalformedResponseException("Unable to parse content range header on 206");
        }
      }

      return new GroupHistory(groupChanges, Optional.empty());
    }
  }

  public GroupJoinInfo getGroupJoinInfo(Optional<byte[]> groupLinkPassword, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    String passwordParam = groupLinkPassword.map(Base64UrlSafe::encodeBytesWithoutPadding).orElse("");
    try (Response response = makeStorageRequest(authorization.toString(),
                                                String.format(GROUPSV2_GROUP_JOIN, passwordParam),
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_JOIN_INFO_HANDLER))
    {
      return GroupJoinInfo.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupExternalCredential getGroupExternalCredential(GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_TOKEN,
                                                "GET",
                                                null,
                                                NO_HANDLER))
    {
      return GroupExternalCredential.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public CurrencyConversions getCurrencyConversions()
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    String response = makeServiceRequest(PAYMENTS_CONVERSIONS, "GET", null);
    try {
      return JsonUtil.fromJson(response, CurrencyConversions.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    }
  }

  public void reportSpam(ServiceId serviceId, String serverGuid, String reportingToken)
      throws NonSuccessfulResponseCodeException, MalformedResponseException, PushNetworkException
  {
    makeServiceRequest(String.format(REPORT_SPAM, serviceId.toString(), serverGuid), "POST", JsonUtil.toJson(new SpamTokenMessage(reportingToken)));
  }

  private static class RegistrationSessionResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException, PushNetworkException {

      if (responseCode == 403) {
        throw new IncorrectRegistrationRecoveryPasswordException();
      } else if (responseCode == 404) {
        throw new NoSuchSessionException();
      } else if (responseCode == 409) {
        RegistrationSessionMetadataJson response;
        try {
          response = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(409);
        }
        if (response.pushChallengedRequired()) {
          throw new PushChallengeRequiredException();
        } else if (response.captchaRequired()) {
          throw new CaptchaRequiredException();
        } else {
          throw new HttpConflictException();
        }
      } else if (responseCode == 502) {
        VerificationCodeFailureResponseBody response;
        try {
          response = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(responseCode);
        }
        throw new ExternalServiceFailureException(response.getPermanentFailure(), response.getReason());
      }
    }
  }


  private static class RegistrationCodeRequestResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException, PushNetworkException {

      if (responseCode == 400) {
        throw new MalformedRequestException();
      } else if (responseCode == 403) {
        throw new IncorrectRegistrationRecoveryPasswordException();
      } else if (responseCode == 404) {
        throw new NoSuchSessionException();
      } else if (responseCode == 409) {
        RegistrationSessionMetadataJson response;
        try {
          response = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(409);
        }
        if (response.pushChallengedRequired()) {
          throw new PushChallengeRequiredException();
        } else if (response.captchaRequired()) {
          throw new CaptchaRequiredException();
        } else {
          throw new HttpConflictException();
        }
      } else if (responseCode == 418) {
        throw new InvalidTransportModeException();
      } else if (responseCode == 429) {
        throw new RegistrationRetryException();
      } else if (responseCode == 440) {
        VerificationCodeFailureResponseBody response;
        try {
          response = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(responseCode);
        }
        throw new ExternalServiceFailureException(response.getPermanentFailure(), response.getReason());
      }
    }
  }


  private static class PatchRegistrationSessionResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException, PushNetworkException {
      switch (responseCode) {
        case 403:
          throw new TokenNotAcceptedException();
        case 404:
          throw new NoSuchSessionException();
        case 409:
          RegistrationSessionMetadataJson response;
          try {
            response = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(409);
          }
          if (response.pushChallengedRequired()) {
            throw new PushChallengeRequiredException();
          } else if (response.captchaRequired()) {
            throw new CaptchaRequiredException();
          } else {
            throw new HttpConflictException();
          }
      }
    }
  }

  private static class RegistrationCodeSubmissionResponseHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body) throws NonSuccessfulResponseCodeException, PushNetworkException {

      switch (responseCode) {
        case 400:
          throw new InvalidTransportModeException();
        case 404:
          throw new NoSuchSessionException();
        case 409:
          RegistrationSessionMetadataJson sessionMetadata;
          try {
            sessionMetadata = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(409);
          }
          if (sessionMetadata.getVerified()) {
            throw new AlreadyVerifiedException();
          } else if (sessionMetadata.getNextVerificationAttempt() == null) {
            // Note: this explicitly requires Verified to be false
            throw new MustRequestNewCodeException();
          } else {
            throw new HttpConflictException();
          }
        case 440:
          VerificationCodeFailureResponseBody codeFailureResponse;
          try {
            codeFailureResponse = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(responseCode);
          }

          throw new ExternalServiceFailureException(codeFailureResponse.getPermanentFailure(), codeFailureResponse.getReason());
      }
    }
  }


  private static RegistrationSessionMetadataResponse parseSessionMetadataResponse(Response response) throws IOException {
    long serverDeliveredTimestamp = 0;
    try {
      String stringValue = response.header(SERVER_DELIVERED_TIMESTAMP_HEADER);
      stringValue = stringValue != null ? stringValue : "0";

      serverDeliveredTimestamp = Long.parseLong(stringValue);
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
    }

    RegistrationSessionMetadataHeaders responseHeaders = new RegistrationSessionMetadataHeaders(serverDeliveredTimestamp);
    RegistrationSessionMetadataJson responseBody = JsonUtil.fromJson(readBodyString(response), RegistrationSessionMetadataJson.class);

    return new RegistrationSessionMetadataResponse(responseHeaders, responseBody, null);
  }

  public static final class GroupHistory {
    private final GroupChanges           groupChanges;
    private final Optional<ContentRange> contentRange;

    public GroupHistory(GroupChanges groupChanges, Optional<ContentRange> contentRange) {
      this.groupChanges = groupChanges;
      this.contentRange = contentRange;
    }

    public GroupChanges getGroupChanges() {
      return groupChanges;
    }

    public boolean hasMore() {
      return contentRange.isPresent();
    }

    /**
     * Valid iff {@link #hasMore()}.
     */
    public int getNextPageStartGroupRevision() {
      return contentRange.get().getRangeEnd() + 1;
    }
  }

  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode, AccountAttributes accountAttributes) throws IOException {
    String responseText = makeServiceRequest(String.format(DEVICE_PATH, verificationCode), "PUT", JsonUtil.toJson(accountAttributes));
    return JsonUtil.fromJson(responseText, VerifyDeviceResponse.class);
  }


  private final class ResumeInfo {
    private final String contentRange;
    private final long   contentStart;

    private ResumeInfo(String contentRange, long offset) {
      this.contentRange = contentRange;
      this.contentStart = offset;
    }
  }
}
