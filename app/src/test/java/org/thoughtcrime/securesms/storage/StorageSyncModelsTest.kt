package org.thoughtcrime.securesms.storage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.google.protobuf.ByteString as ProtoByteString

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageSyncModelsTest {

  private lateinit var mockIdentitiesTable: IdentityTable

  // Helper data
  private val PEAPK_A = byteArrayOf(1, 2, 3, 4, 5)
  private val ID_KEY_A: IdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
  private val ACI_A = ServiceId.ACI.from(UUID.randomUUID())
  private val RECIPIENT_ID_A = RecipientId.from(ACI_A)
  private val TIMESTAMP_A = System.currentTimeMillis() - 10000L

  @Before
  fun setUp() {
    mockkObject(SignalDatabase) // Mock the companion object to control instances
    mockIdentitiesTable = mockk(relaxed = true) // Initialize the mock
    every { SignalDatabase.identities } returns mockIdentitiesTable // Return the mock
  }

  @After
  fun tearDown() {
    unmockkObject(SignalDatabase)
  }

  private fun createRecipientRecord(
    recipientId: RecipientId,
    serviceId: ServiceId?,
    identityKey: IdentityKey? = null,
    storageIdBytes: ByteArray = UUID.randomUUID().toString().toByteArray()
  ): RecipientRecord {
    return RecipientRecord(
      id = recipientId,
      aci = if (serviceId is ServiceId.ACI) serviceId else null,
      pni = if (serviceId is ServiceId.PNI) serviceId else null,
      systemContactId = null,
      systemContactPhotoUri = null,
      systemGivenName = "Test",
      systemFamilyName = "User",
      signalProfileName = RecipientRecord.Name("Signal", "User"),
      signalProfileLastUpdateTimestamp = 0L,
      profileKey = null,
      isBlocked = false,
      blockReason = null,
      messageDisplayPreference = RecipientTable.MessageDisplayPreference.NORMAL,
      muteUntil = 0L,
      lastSeen = 0L,
      unregisteredTimestamp = 0L,
      hasMentionBadge = false,
      hasUnreadSelfMention = false,
      hasPinnedMedia = false,
      hasUnreadPayment = false,
      hasUnreadMoneyRequest = false,
      hiddenState = Recipient.HiddenState.NOT_HIDDEN,
      profileSharing = false,
      storageId = storageIdBytes,
      syncExtras = SyncExtras(
        identityKey = identityKey?.serialize(),
        identityStatus = if (identityKey != null) IdentityTable.VerifiedStatus.DEFAULT else IdentityTable.VerifiedStatus.UNVERIFIED,
        storageProto = null // Not relevant for this specific test focus
      ),
      username = null,
      note = null,
      nickname = RecipientRecord.Name(null, null),
      avatarColor = org.thoughtcrime.securesms.conversation.colors.AvatarColor.A100,
      badge = null,
      extras = null,
      recipientType = RecipientTable.RecipientType.INDIVIDUAL,
      registered = RecipientTable.RegisteredState.REGISTERED, // Assume registered for identity tests
      lastProfileFetch = 0L,
      distributionListId = null,
      callLinkRoomId = null,
      phoneNumberSharingMode = RecipientTable.PhoneNumberSharingMode.DEFAULT,
      phoneNumberDiscoverabilityMode = RecipientTable.PhoneNumberDiscoverabilityMode.DEFAULT,
      lastInteractionTimestamp = 0L,
      mentionSetting = RecipientTable.MentionSetting.DEFAULT,
      isForceArchived = false,
      isMutedNoMentions = false
    )
  }

  @Test
  fun `localToRemoteContact - with peerExtraPublicKey`() {
    // GIVEN
    val recipientRecord = createRecipientRecord(RECIPIENT_ID_A, ACI_A, ID_KEY_A)
    val identityRecord = IdentityRecord(
      RECIPIENT_ID_A,
      ID_KEY_A,
      IdentityTable.VerifiedStatus.DEFAULT,
      true,
      TIMESTAMP_A,
      true,
      PEAPK_A
    )
    every { mockIdentitiesTable.getIdentityRecord(RECIPIENT_ID_A) } returns Optional.of(identityRecord)

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(recipientRecord)
    val contactProto = contactStorageRecord.proto.contact!! // Get the underlying ContactRecord proto

    // THEN
    assertNotNull(contactProto.peerExtraPublicKey)
    assertContentEquals(PEAPK_A, contactProto.peerExtraPublicKey!!.toByteArray())
    assertEquals(TIMESTAMP_A, contactProto.peerExtraPublicKeyTimestamp)
  }

  @Test
  fun `localToRemoteContact - without peerExtraPublicKey`() {
    // GIVEN
    val recipientRecord = createRecipientRecord(RECIPIENT_ID_A, ACI_A, ID_KEY_A)
    val identityRecord = IdentityRecord( // PEAPK is null
      RECIPIENT_ID_A,
      ID_KEY_A,
      IdentityTable.VerifiedStatus.DEFAULT,
      true,
      TIMESTAMP_A,
      true,
      null
    )
    every { mockIdentitiesTable.getIdentityRecord(RECIPIENT_ID_A) } returns Optional.of(identityRecord)

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(recipientRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertTrue(contactProto.peerExtraPublicKey == null || contactProto.peerExtraPublicKey!!.isEmpty)
    assertEquals(0L, contactProto.peerExtraPublicKeyTimestamp) // Default value for long if not set
  }

  @Test
  fun `localToRemoteContact - no identity record`() {
    // GIVEN
    val recipientRecord = createRecipientRecord(RECIPIENT_ID_A, ACI_A, ID_KEY_A)
    every { mockIdentitiesTable.getIdentityRecord(RECIPIENT_ID_A) } returns Optional.empty()

    // WHEN
    val contactStorageRecord = StorageSyncModels.localToRemoteRecord(recipientRecord)
    val contactProto = contactStorageRecord.proto.contact!!

    // THEN
    assertTrue(contactProto.peerExtraPublicKey == null || contactProto.peerExtraPublicKey!!.isEmpty)
    assertEquals(0L, contactProto.peerExtraPublicKeyTimestamp)
  }
}
