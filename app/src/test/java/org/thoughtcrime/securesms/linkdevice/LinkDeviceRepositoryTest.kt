package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import com.google.protobuf.ByteString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.SvrValues
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.link.LinkDeviceApi
import org.whispersystems.signalservice.api.link.LinkedDeviceVerificationCodeResponse
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.AccountEntropyPool
import org.whispersystems.signalservice.internal.push.ProvisioningProtos
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkDeviceRepositoryTest {

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockSignalNetwork: SignalNetwork

    @Mock
    private lateinit var mockLinkDeviceApi: LinkDeviceApi

    @Mock
    private lateinit var mockSignalDatabase: SignalDatabase

    @Mock
    private lateinit var mockIdentitiesTable: IdentityTable

    @Mock
    private lateinit var mockRecipientSelf: Recipient

    @Mock
    private lateinit var mockSignalStore: SignalStore

    @Mock
    private lateinit var mockSvrValues: SvrValues

    @Mock
    private lateinit var mockAccountManager: SignalServiceAccountManager

    @Mock
    private lateinit var mockAppDependencies: AppDependencies

    @Captor
    private lateinit var byteArrayCaptor: ArgumentCaptor<ByteArray?>


    private lateinit var mockedStaticSignalNetwork: MockedStatic<SignalNetwork>
    private lateinit var mockedStaticSignalDatabase: MockedStatic<SignalDatabase>
    private lateinit var mockedStaticRecipient: MockedStatic<Recipient>
    private lateinit var mockedStaticSignalStore: MockedStatic<SignalStore>
    private lateinit var mockedStaticProfileKeyUtil: MockedStatic<ProfileKeyUtil>
    private lateinit var mockedStaticAppDependencies: MockedStatic<AppDependencies>


    private val selfRecipientId = RecipientId.from(1L)
    private val selfAci: ServiceId.ACI = ServiceId.ACI.parseOrThrow("00000000-0000-0000-0000-000000000001")
    private val selfPni: ServiceId.PNI = ServiceId.PNI.parseOrThrow("PNI:00000000-0000-0000-0000-000000000002")
    private val selfE164 = "+15550001234"


    @Before
    fun setUp() {
        // Mock static methods
        mockedStaticSignalNetwork = mockStatic(SignalNetwork::class.java)
        mockedStaticSignalDatabase = mockStatic(SignalDatabase::class.java)
        mockedStaticRecipient = mockStatic(Recipient::class.java)
        mockedStaticSignalStore = mockStatic(SignalStore::class.java)
        mockedStaticProfileKeyUtil = mockStatic(ProfileKeyUtil::class.java)
        mockedStaticAppDependencies = mockStatic(AppDependencies::class.java)


        `when`(SignalNetwork.linkDevice).thenReturn(mockLinkDeviceApi)
        `when`(SignalDatabase.identities()).thenReturn(mockIdentitiesTable)
        `when`(Recipient.self()).thenReturn(mockRecipientSelf)
        `when`(mockRecipientSelf.id).thenReturn(selfRecipientId)
        `when`(SignalStore.account()).thenReturn(mockAccountManager) // Simplification, adjust if SignalStore.account() returns specific account object
        `when`(SignalStore.svr()).thenReturn(mockSvrValues)
        `when`(SignalStore.backup()).thenReturn(mock()) // Mock backup store if methods are called

        // Setup for SignalStore.account() fields
        `when`(mockAccountManager.e164).thenReturn(selfE164)
        `when`(mockAccountManager.aci).thenReturn(selfAci)
        `when`(mockAccountManager.pni).thenReturn(selfPni)
        `when`(mockAccountManager.aciIdentityKey).thenReturn(IdentityKeyPair(Curve.generateKeyPair()))
        `when`(mockAccountManager.pniIdentityKey).thenReturn(IdentityKeyPair(Curve.generateKeyPair()))
        `when`(mockAccountManager.accountEntropyPool).thenReturn(AccountEntropyPool(ByteArray(32)))

        `when`(mockSvrValues.masterKey).thenReturn(mock(MasterKey::class.java))
        `when`(ProfileKeyUtil.getSelfProfileKey()).thenReturn(org.signal.libsignal.zkgroup.profiles.ProfileKey(ByteArray(32)))
        `when`(SignalStore.backup().mediaRootBackupKey).thenReturn(MediaRootBackupKey(ByteArray(32)))

        // Mock AppDependencies to return our mocked SignalServiceAccountManager
        `when`(AppDependencies.getSignalServiceAccountManager()).thenReturn(mockAccountManager)
         // Mock getDeviceVerificationCode to return a successful response
        val mockVerificationCodeResponse = LinkedDeviceVerificationCodeResponse("test_token", "test_code")
        `when`(mockLinkDeviceApi.getDeviceVerificationCode()).thenReturn(NetworkResult.success(mockVerificationCodeResponse))

    }

    @Test
    fun addDevice_sendsPeerExtraPublicKey_whenAvailable() {
        // Arrange
        val testPeerExtraPublicKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        `when`(mockIdentitiesTable.getExtraPublicKey(selfRecipientId)).thenReturn(testPeerExtraPublicKey)

        val mockUri = mock(Uri::class.java)
        `when`(mockUri.isHierarchical).thenReturn(true)
        `when`(mockUri.getQueryParameter("uuid")).thenReturn("test_uuid")
        `when`(mockUri.getQueryParameter("pub_key")).thenReturn(Base64.encodeBytes(Curve.generateKeyPair().publicKey.serialize()))

        // Mock the linkDevice call to capture arguments and return success
        `when`(mockLinkDeviceApi.linkDevice(
            any(String::class.java), // e164
            any(ServiceId.ACI::class.java), // aci
            any(ServiceId.PNI::class.java), // pni
            any(String::class.java), // deviceIdentifier
            any(ECPublicKey::class.java), // deviceKey
            any(IdentityKeyPair::class.java), // aciIdentityKeyPair
            any(IdentityKeyPair::class.java), // pniIdentityKeyPair
            any(org.signal.libsignal.zkgroup.profiles.ProfileKey::class.java), // profileKey
            any(AccountEntropyPool::class.java), // accountEntropyPool
            any(MasterKey::class.java), // masterKey
            any(MediaRootBackupKey::class.java), // mediaRootBackupKey
            any(String::class.java), // code
            any(), // ephemeralMessageBackupKey (nullable)
            byteArrayCaptor.capture() // localPeerExtraPublicKey
        )).thenReturn(NetworkResult.success(Unit))


        // Action
        LinkDeviceRepository.addDevice(mockUri, null)

        // Assert
        verify(mockLinkDeviceApi).linkDevice(
            eq(selfE164),
            eq(selfAci),
            eq(selfPni),
            eq("test_uuid"),
            any(ECPublicKey::class.java),
            any(IdentityKeyPair::class.java),
            any(IdentityKeyPair::class.java),
            any(org.signal.libsignal.zkgroup.profiles.ProfileKey::class.java),
            any(AccountEntropyPool::class.java),
            any(MasterKey::class.java),
            any(MediaRootBackupKey::class.java),
            eq("test_code"),
            eq(null), // ephemeralMessageBackupKey
            eq(testPeerExtraPublicKey) // Verifying the captured value directly
        )
        assertNotNull(byteArrayCaptor.value)
        assertArrayEquals(testPeerExtraPublicKey, byteArrayCaptor.value)
    }

    @Test
    fun addDevice_sendsNullPeerExtraPublicKey_whenNotAvailable() {
        // Arrange
        `when`(mockIdentitiesTable.getExtraPublicKey(selfRecipientId)).thenReturn(null) // Key not available

        val mockUri = mock(Uri::class.java)
        `when`(mockUri.isHierarchical).thenReturn(true)
        `when`(mockUri.getQueryParameter("uuid")).thenReturn("test_uuid")
        `when`(mockUri.getQueryParameter("pub_key")).thenReturn(Base64.encodeBytes(Curve.generateKeyPair().publicKey.serialize()))

        `when`(mockLinkDeviceApi.linkDevice(
            any(String::class.java), any(ServiceId.ACI::class.java), any(ServiceId.PNI::class.java),
            any(String::class.java), any(ECPublicKey::class.java), any(IdentityKeyPair::class.java),
            any(IdentityKeyPair::class.java), any(org.signal.libsignal.zkgroup.profiles.ProfileKey::class.java),
            any(AccountEntropyPool::class.java), any(MasterKey::class.java), any(MediaRootBackupKey::class.java),
            any(String::class.java), any(), byteArrayCaptor.capture()
        )).thenReturn(NetworkResult.success(Unit))

        // Action
        LinkDeviceRepository.addDevice(mockUri, null)

        // Assert
         verify(mockLinkDeviceApi).linkDevice(
            eq(selfE164),
            eq(selfAci),
            eq(selfPni),
            eq("test_uuid"),
            any(ECPublicKey::class.java),
            any(IdentityKeyPair::class.java),
            any(IdentityKeyPair::class.java),
            any(org.signal.libsignal.zkgroup.profiles.ProfileKey::class.java),
            any(AccountEntropyPool::class.java),
            any(MasterKey::class.java),
            any(MediaRootBackupKey::class.java),
            eq("test_code"),
            eq(null),
            eq(null) // Asserting that null is passed when key is not available
        )
        assertEquals(null, byteArrayCaptor.value)
    }

    // TODO: Add more tests for LinkDeviceRepository an LQR LinkDeviceResult states if needed
}
