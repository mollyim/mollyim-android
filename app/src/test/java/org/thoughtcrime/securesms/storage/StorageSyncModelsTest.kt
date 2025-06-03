package org.thoughtcrime.securesms.storage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.protobuf.ByteString as ProtoByteString
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.model.SyncExtras
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageSyncModelsTest {

  @get:Rule
  val mockitoRule: MockitoRule = MockitoJUnit.rule() // For any Mockito usage if needed alongside MockK

  private lateinit var mockIdentitiesTable: IdentityTable
  private lateinit var mockRecipientSelf: Recipient
  private lateinit var mockSignalStoreAccount: SignalStore.Account // Specific mock for SignalStore.account()

  // Helper data
  private val PEAPK_A = byteArrayOf(1, 2, 3, 4, 5)
  private val ID_KEY_A: IdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
  private val SELF_ACI_STRING = "00000000-1111-0000-0000-000000000000"
  private val SELF_ACI = ServiceId.ACI.from(UUID.fromString(SELF_ACI_STRING))
  private val SELF_RECIPIENT_ID = RecipientId.from(1L) // Assign a specific ID for self

  private val OTHER_ACI_STRING = "00000000-2222-0000-0000-000000000000"
  private val OTHER_ACI = ServiceId.ACI.from(UUID.fromString(OTHER_ACI_STRING))
  private val OTHER_RECIPIENT_ID = RecipientId.from(2L)

  private val TIMESTAMP_A = System.currentTimeMillis() - 10000L

  @Before
  fun setUp() {
    mockkObject(SignalDatabase)
    mockkStatic(Recipient::class)
    mockkStatic(SignalStore::class) // Mock static accessor for SignalStore

    mockIdentitiesTable = mockk(relaxed = true)
    mockRecipientSelf = mockk(relaxed = true)
    mockSignalStoreAccount = mockk(relaxed = true) // Mock for SignalStore.account()

    every { SignalDatabase.identities } returns mockIdentitiesTable
    every { Recipient.self() } returns mockRecipientSelf
    every { SignalStore.account() } returns mockSignalStoreAccount // Make SignalStore.account() return our mock

    // Setup self recipient
    every { mockRecipientSelf.id } returns SELF_RECIPIENT_ID
    every { mockRecipientSelf.aci } returns Optional.of(SELF_ACI)
    every { mockRecipientSelf.e164 } returns Optional.empty() // ACI is primary for identity record lookup for self

    // Setup SignalStore.account() to return self ACI
    every { mockSignalStoreAccount.aci } returns SELF_ACI
  }

  @After
  fun tearDown() {
    unmockkObject(SignalDatabase)
    unmockkObject(Recipient::class)
    unmockkObject(SignalStore::class)
  }

  private fun createRecipientRecord(
    recipientId: RecipientId,
    serviceId: ServiceId?,
    isSelf: Boolean, // Added to differentiate
    identityKey: IdentityKey? = null,
    storageIdBytes: ByteArray = UUID.randomUUID().toString().toByteArray()
  ): RecipientRecord {
    // Simplified RecipientRecord creation, focusing on fields relevant to the test
    return RecipientRecord(
      id = recipientId,
      aci = if (serviceId is ServiceId.ACI) serviceId else null,
      pni = if (serviceId is ServiceId.PNI) serviceId else null,
      e164 = null, // Keep simple for these tests
      email = null,
      groupId = null,
      distributionListId = null,
      callLinkRoomId = null,
      recipientType = RecipientTable.RecipientType.INDIVIDUAL,
      isBlocked = false,
      muteUntil = 0L,
      messageVibrateState = RecipientTable.VibrateState.DEFAULT,
      callVibrateState = RecipientTable.VibrateState.DEFAULT,
      messageRingtone = null,
      callRingtone = null,
      expireMessages = 0,
      expireTimerVersion = 1,
      registered = RecipientTable.RegisteredState.REGISTERED,
      profileKey = null,
      expiringProfileKeyCredential = null,
      systemProfileName = RecipientRecord.Name(null, null),
      systemDisplayName = null,
      systemContactPhotoUri = null,
      systemPhoneLabel = null,
      systemContactUri = null,
      signalProfileName = RecipientRecord.Name("Signal", if (isSelf) "Self" else "User"),
      signalProfileAvatar = null,
      profileAvatarFileDetails = org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails.NO_DETAILS,
      profileSharing = false,
      lastProfileFetch = 0L,
      notificationChannel = null,
      sealedSenderAccessMode = RecipientTable.SealedSenderAccessMode.UNKNOWN,
      capabilities = RecipientRecord.Capabilities.UNKNOWN, // Not testing this part here
      storageId = storageIdBytes,
      mentionSetting = RecipientTable.MentionSetting.DEFAULT,
      wallpaper = null,
      chatColors = null,
      avatarColor = org.thoughtcrime.securesms.conversation.colors.AvatarColor.A100,
      about = null,
      aboutEmoji = null,
      syncExtras = SyncExtras(
        identityKey = identityKey?.serialize(),
        identityStatus = if (identityKey != null) IdentityTable.VerifiedStatus.DEFAULT else IdentityTable.VerifiedStatus.UNVERIFIED,
        storageProto = null,
        groupMasterKey = null,
        isArchived = false,
        isForcedUnread = false,
        unregisteredTimestamp = 0L,
        systemNickname = null,
        pniSignatureVerified = false
      ),
      extras = null,
      hasGroupsInCommon = false,
      badges = emptyList(),
      needsPniSignature = false,
      hiddenState = Recipient.HiddenState.NOT_HIDDEN,
      phoneNumberSharing = RecipientTable.PhoneNumberSharingState.UNKNOWN,
      nickname = RecipientRecord.Name(null, null),
      note = null
    )
  }

  @Test
  fun `localToRemoteContact - for self - includes peerExtraPublicKey and timestamp`() {
    // GIVEN
    val selfRecord = createRecipientRecord(SELF_RECIPIENT_ID, SELF_ACI, isSelf = true, identityKey = ID_KEY_A)
    val identityRecord = IdentityRecord(
      SELF_RECIPIENT_ID, ID_KEY_A, IdentityTable.VerifiedStatus.DEFAULT, true, TIMESTAMP_A, true, PEAPK_A
    )
    // Expect getIdentityRecord to be called with self's ACI string
    every { mockIdentitiesTable.getIdentityRecord(SELF_ACI.toString()) } returns Optional.of(identityRecord)

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(selfRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertTrue(contactProto.hasPeerExtraPublicKey())
    assertContentEquals(PEAPK_A, contactProto.peerExtraPublicKey!!.toByteArray())
    assertTrue(contactProto.hasPeerExtraPublicKeyTimestamp())
    assertEquals(TIMESTAMP_A, contactProto.peerExtraPublicKeyTimestamp)
  }

  @Test
  fun `localToRemoteContact - for self - no peerExtraPublicKey in DB - fields not set`() {
    // GIVEN
    val selfRecord = createRecipientRecord(SELF_RECIPIENT_ID, SELF_ACI, isSelf = true, identityKey = ID_KEY_A)
    val identityRecord = IdentityRecord( // PEAPK is null
      SELF_RECIPIENT_ID, ID_KEY_A, IdentityTable.VerifiedStatus.DEFAULT, true, TIMESTAMP_A, true, null
    )
    every { mockIdentitiesTable.getIdentityRecord(SELF_ACI.toString()) } returns Optional.of(identityRecord)

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(selfRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertFalse(contactProto.hasPeerExtraPublicKey())
    assertTrue(contactProto.peerExtraPublicKey == null || contactProto.peerExtraPublicKey!!.isEmpty)
    assertFalse(contactProto.hasPeerExtraPublicKeyTimestamp()) // Timestamp should not be set if key is not set
    assertEquals(0L, contactProto.peerExtraPublicKeyTimestamp) // Default value for long if not set
  }

  @Test
  fun `localToRemoteContact - for self - no IdentityRecord in DB - fields not set`() {
    // GIVEN
    val selfRecord = createRecipientRecord(SELF_RECIPIENT_ID, SELF_ACI, isSelf = true, identityKey = ID_KEY_A)
    every { mockIdentitiesTable.getIdentityRecord(SELF_ACI.toString()) } returns Optional.empty()

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(selfRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertFalse(contactProto.hasPeerExtraPublicKey())
    assertTrue(contactProto.peerExtraPublicKey == null || contactProto.peerExtraPublicKey!!.isEmpty)
    assertFalse(contactProto.hasPeerExtraPublicKeyTimestamp())
    assertEquals(0L, contactProto.peerExtraPublicKeyTimestamp)
  }

  @Test
  fun `localToRemoteContact - for other recipient - does not include peerExtraPublicKey fields`() {
    // GIVEN
    val otherRecord = createRecipientRecord(OTHER_RECIPIENT_ID, OTHER_ACI, isSelf = false, identityKey = ID_KEY_A)
    // No need to mock getIdentityRecord for OTHER_RECIPIENT_ID if the logic correctly skips for non-self

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(otherRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertFalse(contactProto.hasPeerExtraPublicKey())
    assertTrue(contactProto.peerExtraPublicKey == null || contactProto.peerExtraPublicKey!!.isEmpty)
    assertFalse(contactProto.hasPeerExtraPublicKeyTimestamp())
    assertEquals(0L, contactProto.peerExtraPublicKeyTimestamp)
  }
}
