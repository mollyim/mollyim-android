package org.thoughtcrime.securesms.export

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
// import org.mockito.Mockito.anyString // Not used directly, but common
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import android.os.Environment // For Environment.MEDIA_MOUNTED
import org.thoughtcrime.securesms.export.util.FileSystemOps // New import
// OkHttpClient already imported via ChatExportHelper constructor change
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After // For MockWebServer shutdown
import kotlinx.coroutines.runBlocking // For testing suspend functions
import kotlinx.coroutines.ExperimentalCoroutinesApi // If using test dispatchers
import org.mockito.ArgumentCaptor // Keep for File captor
import org.mockito.Captor // Keep if other captors are used, or remove if only File captor
import java.io.File
// import java.io.FileOutputStream // No longer directly used for mocking output stream
import java.io.OutputStream // Changed from FileOutputStream for mock
import java.io.IOException
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord // Using this for its properties
import org.thoughtcrime.securesms.export.model.ExportedMessage // Corrected model import
import org.thoughtcrime.securesms.export.model.ExportErrorType // Corrected model import
import org.thoughtcrime.securesms.export.model.ExportResult // Corrected model import
import org.thoughtcrime.securesms.database.model.Quote // Assuming this is the correct Quote model if used by MessageRecord
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
// import org.thoughtcrime.securesms.mms.TextSlide // Example, if specific slide types were tested
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
// import org.thoughtcrime.securesms.util.MediaUtil // If used by ChatExportHelper internally for attachments


@RunWith(MockitoJUnitRunner::class)
class ChatExportHelperTest { // Renamed class

    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockMessageTable: MessageTable
    @Mock
    private lateinit var mockAttachmentTable: AttachmentTable
    @Mock
    private lateinit var mockOkHttpClientInternal: OkHttpClient // This is the one injected if we want to mock it, distinct from okHttpClientForTest

    private lateinit var chatExportHelper: ChatExportHelper

    // Inside ChatExportHelperTest class
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClientForTest: OkHttpClient // Use a real OkHttpClient for MockWebServer

    // @Captor // fileOutputStreamCaptor removed as we mock OutputStream directly
    // private lateinit var fileOutputStreamCaptor: ArgumentCaptor<ByteArray>

    @Mock
    private lateinit var mockFileSystemOps: FileSystemOps // Added mock for FileSystemOps

    // Conceptual: Interface to abstract static Environment.getExternalStorageState()
    // interface EnvironmentStateProvider {
    //     fun getExternalStorageState(): String
    // }
    // @Mock
    // private lateinit var mockEnvironmentStateProvider: EnvironmentStateProvider

    // Mocked Recipient.Live objects
    private lateinit var mockSenderRecipient: Recipient
    // private lateinit var mockQuoteAuthorRecipient: Recipient // Will be used in more detailed tests

    @Before
    fun setUp() {
        mockSenderRecipient = mock(Recipient::class.java)
        `when`(mockSenderRecipient.id).thenReturn(RecipientId.from(123L))
        // Assuming getPreferredExportIdentifier is the method to be called
        `when`(mockSenderRecipient.getPreferredExportIdentifier()).thenReturn("sender_export_id_123")
        `when`(mockSenderRecipient.getDisplayName(mockContext)).thenReturn("Mock Sender")

        // mockQuoteAuthorRecipient = mock(Recipient::class.java)
        // `when`(mockQuoteAuthorRecipient.id).thenReturn(RecipientId.from(456L))
        // `when`(mockQuoteAuthorRecipient.getPreferredExportIdentifier()).thenReturn("quote_author_export_id_456")
        // `when`(mockQuoteAuthorRecipient.getDisplayName(mockContext)).thenReturn("Mock Quote Author")

        // Note: Static mocking for Recipient.resolved() is NOT set up here.
        // Tests relying on specific resolved recipients will need PowerMockito or similar.

        // Setup for API tests
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClientForTest = OkHttpClient.Builder().build() // This client hits MockWebServer

        // Use okHttpClientForTest for ChatExportHelper when testing API calls
        chatExportHelper = ChatExportHelper(
            mockContext,
            mockMessageTable,
            mockAttachmentTable,
            okHttpClientForTest, // Use the client that can connect to MockWebServer for these tests
            mockFileSystemOps     // Add the new mock
        )

        // `when`(mockEnvironmentStateProvider.getExternalStorageState()).thenReturn(Environment.MEDIA_MOUNTED)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // Helper to create a mock MessageRecord
    private fun createMockMessageRecord(
        id: Long, threadId: Long, fromRecipientIdValue: Long, dateSent: Long, dateReceived: Long,
        body: String?, type: Long, quote: Quote? = null, attachments: List<Slide> = emptyList(),
        isViewOnce: Boolean = false, isRemoteDelete: Boolean = false
    ): MessageRecord {
        val mockRecord = mock(MmsMessageRecord::class.java) // Using MmsMessageRecord for SlideDeck & other fields
        val fromRecipientId = RecipientId.from(fromRecipientIdValue)

        `when`(mockRecord.id).thenReturn(id)
        `when`(mockRecord.threadId).thenReturn(threadId)

        // Simplification: MessageRecord.getFromRecipient returns Recipient, not RecipientId directly in some contexts.
        // For this test, we'll assume fromRecipient.id is what ChatExportHelper uses.
        // If ChatExportHelper calls record.fromRecipient.getSomeSpecificId(), that needs mocking.
        // Here, we mock the Recipient object that would be returned by record.getFromRecipient()
        // and then ensure its 'id' field and other methods return what we expect.
        val recipientForRecord = if (fromRecipientIdValue == 123L) mockSenderRecipient else mock(Recipient::class.java)
        if (fromRecipientIdValue != 123L) { // Basic mocking for other recipients
            `when`(recipientForRecord.id).thenReturn(fromRecipientId)
            `when`(recipientForRecord.getPreferredExportIdentifier()).thenReturn("other_id_${fromRecipientIdValue}")
            `when`(recipientForRecord.getDisplayName(mockContext)).thenReturn("Other User")
        }
         `when`(mockRecord.fromRecipient).thenReturn(recipientForRecord)


        `when`(mockRecord.dateSent).thenReturn(dateSent)
        `when`(mockRecord.dateReceived).thenReturn(dateReceived)
        `when`(mockRecord.body).thenReturn(body?.let { CharSequenceWrapper(it) })
        `when`(mockRecord.type).thenReturn(type) // Store the raw type
        `when`(mockRecord.quote).thenReturn(quote)
        `when`(mockRecord.isViewOnce).thenReturn(isViewOnce)
        `when`(mockRecord.isRemoteDelete).thenReturn(isRemoteDelete)

        val slideDeck = SlideDeck(attachments)
        `when`(mockRecord.slideDeck).thenReturn(slideDeck)

        // Mock message type helper methods directly on the record
        `when`(mockRecord.isOutgoing).thenAnswer { (mockRecord.type and MessageTable.OUTGOING_MESSAGE_BIT) != 0L }
        `when`(mockRecord.isIncoming).thenAnswer { (mockRecord.type and MessageTable.BASE_INBOX_TYPE) != 0L && (mockRecord.type and MessageTable.OUTGOING_MESSAGE_BIT) == 0L }
        `when`(mockRecord.isJoin).thenAnswer { (mockRecord.type and MessageTable.GROUP_V2_JOIN_BIT) != 0L }
        // Add other type checks like isGroupUpdate, isEndSession etc. as needed by ChatExportHelper's logic

        return mockRecord
    }

    // Wrapper for CharSequence as Mockito has issues with it sometimes
    class CharSequenceWrapper(private val value: String) : CharSequence by value {
        override fun toString(): String = value
        override fun equals(other: Any?): Boolean = value == (other as? CharSequence)?.toString()
        override fun hashCode(): Int = value.hashCode()
    }


    @Test
    fun `getMessagesForThread with empty cursor should return Success emptyList`() {
        // MessageTable.Reader is an interface, MmsReader is a concrete class.
        // ChatExportHelper uses 'for (messageRecord in reader)' which implies reader is Iterable.
        // We can mock the reader to return an empty iterator.
        val mockReader = mock(MessageTable.MmsReader::class.java) as Iterable<MessageRecord>
        `when`(mockReader.iterator()).thenReturn(emptyList<MessageRecord>().iterator())
        `when`(mockMessageTable.getConversation(anyLong())).thenReturn(mockReader as MessageTable.MmsReader)


        val result = chatExportHelper.getMessagesForThread(1L)

        assertTrue(result is ExportResult.Success)
        assertEquals(0, (result as ExportResult.Success).data.size)
    }

    @Test
    fun `getMessagesForThread with database error should return Error DatabaseError`() {
        `when`(mockMessageTable.getConversation(anyLong())).thenThrow(SQLiteException("Test DB error"))

        val result = chatExportHelper.getMessagesForThread(1L)

        assertTrue(result is ExportResult.Error)
        val error = (result as ExportResult.Error).error
        assertTrue(error is ExportErrorType.DatabaseError)
        assertEquals("Failed to retrieve messages from the database.", error.userMessage)
        assertTrue(error.logMessage.contains("Test DB error"))
    }

    @Test
    fun `getMessagesForThread maps single message correctly`() {
        // This test relies on the setup of mockSenderRecipient for RecipientId 123L
        // And the behavior of createMockMessageRecord to use it when fromRecipientIdValue is 123L.
        // The static Recipient.resolved() call in ChatExportHelper is bypassed by mocking record.fromRecipient.

        val record1 = createMockMessageRecord(
            id = 1L, threadId = 10L, fromRecipientIdValue = 123L, // This ID should map to mockSenderRecipient
            dateSent = 1000L, dateReceived = 1001L, body = "Hello", type = MessageTable.BASE_INBOX_TYPE
        )

        val mockReader = mock(MessageTable.MmsReader::class.java) as Iterable<MessageRecord>
        `when`(mockReader.iterator()).thenReturn(listOf(record1).iterator())
        `when`(mockMessageTable.getConversation(10L)).thenReturn(mockReader as MessageTable.MmsReader)

        val result = chatExportHelper.getMessagesForThread(10L)

        assertTrue(result is ExportResult.Success)
        val exportedMessages = (result as ExportResult.Success).data
        assertEquals(1, exportedMessages.size)

        val exportedMsg1 = exportedMessages[0]
        assertEquals(1L, exportedMsg1.id)
        assertEquals("Hello", exportedMsg1.body)
        assertEquals("sender_export_id_123", exportedMsg1.senderId)
        assertEquals("Mock Sender", exportedMsg1.senderName)
        assertEquals("incoming", exportedMsg1.messageType)
    }

    @Test
    fun `getMessagesForThread check chronological order`() {
        // record1 is older by dateSent
        val record1 = createMockMessageRecord(1L, 1L, 1L, 1000L, 2000L, "M1", MessageTable.BASE_INBOX_TYPE)
        val record2 = createMockMessageRecord(2L, 1L, 1L, 3000L, 4000L, "M2", MessageTable.BASE_INBOX_TYPE)

        // MessageTable.getConversation usually returns newest first from DB perspective.
        // ChatExportHelper then reverses this list.
        val mockReader = mock(MessageTable.MmsReader::class.java) as Iterable<MessageRecord>
        `when`(mockReader.iterator()).thenReturn(listOf(record2, record1).iterator()) // Simulate DB returning newest (record2) first
        `when`(mockMessageTable.getConversation(1L)).thenReturn(mockReader as MessageTable.MmsReader)

        val result = chatExportHelper.getMessagesForThread(1L)
        assertTrue(result is ExportResult.Success)
        val data = (result as ExportResult.Success).data
        assertEquals(2, data.size)
        assertEquals(1L, data[0].id) // M1 (record1) should be first (oldest)
        assertEquals(2L, data[1].id) // M2 (record2) should be second (newest)
    }

    // TODO: Add more detailed tests for:
    // - Quote mapping: This will require mocking record.getQuote and Recipient.resolved for quote.getAuthor().
    // - Attachment mapping (various slide types).
    // - Different message types (outgoing, various system messages).
    // - isViewOnce, isRemoteDelete flags.
}
