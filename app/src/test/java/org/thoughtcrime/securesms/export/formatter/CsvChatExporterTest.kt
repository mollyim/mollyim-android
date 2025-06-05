package org.thoughtcrime.securesms.export.formatter

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.export.model.ExportedAttachment
import org.thoughtcrime.securesms.export.model.ExportedMessage
import org.thoughtcrime.securesms.export.model.ExportedQuote

class CsvChatExporterTest {

    private lateinit var exporter: CsvChatExporter

    private val expectedHeader = "\"Message ID\"," +
                                 "\"Thread ID\"," +
                                 "\"Sender ID\"," +
                                 "\"Sender Name\"," +
                                 "\"Date Sent (UTC ms)\"," +
                                 "\"Date Received (UTC ms)\"," +
                                 "\"Body\"," +
                                 "\"Message Type\"," +
                                 "\"Is View Once\"," +
                                 "\"Is Remote Delete\"," +
                                 "\"Quoted Author ID\"," +
                                 "\"Quoted Author Name\"," +
                                 "\"Quoted Text\"," +
                                 "\"Quoted Original Timestamp (UTC ms)\"," +
                                 "\"Attachments (Content Type|File Name|Local URI|Size|Is VoiceNote|Is Sticker)\""

    @Before
    fun setUp() {
        exporter = CsvChatExporter()
    }

    private fun parseCsvLine(line: String): List<String> {
        // Basic CSV parsing: splits by comma, handles quoted fields containing commas.
        // This is a simplified parser for testing; a robust CSV library would be better for complex CSV.
        val result = mutableListOf<String>()
        var currentPos = 0
        var inQuotes = false
        var fieldStart = 0

        while (currentPos < line.length) {
            val char = line[currentPos]
            if (char == '"') {
                // This simplistic quote handling assumes quotes only appear at start/end of field or as "" for escaping
                // If it's an escaped quote (""), we need to advance past it.
                if (currentPos + 1 < line.length && line[currentPos + 1] == '"' && inQuotes) {
                    currentPos++ // Skip the second quote of an escaped pair
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == ',' && !inQuotes) {
                result.add(line.substring(fieldStart, currentPos).unquoteCsvField())
                fieldStart = currentPos + 1
            }
            currentPos++
        }
        result.add(line.substring(fieldStart).unquoteCsvField()) // Add last field
        return result
    }

    private fun String.unquoteCsvField(): String {
        if (this.startsWith("\"") && this.endsWith("\"")) {
            return this.substring(1, this.length - 1).replace("\"\"", "\"")
        }
        return this
    }

    @Test
    fun `export empty list should produce only header row`() {
        val messages = emptyList<ExportedMessage>()
        val csvOutput = exporter.export(messages).trim() // Trim to remove potential trailing newline from StringBuilder
        assertEquals(expectedHeader, csvOutput)
    }

    @Test
    fun `export single message with minimal data`() {
        val message = ExportedMessage(
            id = 1L,
            threadId = 10L,
            senderId = "sender123",
            senderName = null,
            dateSent = 1670000000000L,
            dateReceived = 1670000001000L,
            body = null,
            messageType = "incoming",
            attachments = emptyList(),
            quote = null,
            isViewOnce = false,
            isRemoteDelete = false
        )
        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        assertEquals(2, lines.size) // Header + 1 data row
        assertEquals(expectedHeader, lines[0])

        val values = parseCsvLine(lines[1])
        assertEquals("1", values[0])
        assertEquals("10", values[1])
        assertEquals("sender123", values[2])
        assertEquals("", values[3]) // senderName null
        assertEquals("1670000000000", values[4])
        assertEquals("1670000001000", values[5])
        assertEquals("", values[6]) // body null
        assertEquals("incoming", values[7])
        assertEquals("false", values[8])
        assertEquals("false", values[9])
        assertEquals("", values[10]) // quoteAuthorId null
        assertEquals("", values[11]) // quoteAuthorName null
        assertEquals("", values[12]) // quoteText null
        assertEquals("", values[13]) // quoteTimestamp null
        assertEquals("", values[14]) // attachments empty
    }

    @Test
    fun `export single message with all data and multiple attachments`() {
        val attachment1 = ExportedAttachment("image/jpeg", "photo.jpg", "content://uri/1", null, 12345L, false, false)
        val attachment2 = ExportedAttachment("audio/ogg", "voice.ogg", "content://uri/2", null, 54321L, true, false)
        val quote = ExportedQuote("quoteAuthor456", "Quote Author", "Original message", 1669000000000L)
        val message = ExportedMessage(
            id = 2L,
            threadId = 20L,
            senderId = "sender456",
            senderName = "Sender Name",
            dateSent = 1671000000000L,
            dateReceived = 1671000001000L,
            body = "Hello, CSV world!",
            messageType = "outgoing",
            attachments = listOf(attachment1, attachment2),
            quote = quote,
            isViewOnce = true,
            isRemoteDelete = true
        )
        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        assertEquals(2, lines.size)
        val values = parseCsvLine(lines[1])

        assertEquals("2", values[0])
        assertEquals("Hello, CSV world!", values[6])
        assertEquals("true", values[8])
        assertEquals("true", values[9])
        assertEquals("quoteAuthor456", values[10])
        assertEquals("Quote Author", values[11])
        assertEquals("Original message", values[12])
        assertEquals("1669000000000", values[13])

        val expectedAttachmentString = "image/jpeg|photo.jpg|content://uri/1|12345|false|false;" +
                                       "audio/ogg|voice.ogg|content://uri/2|54321|true|false"
        assertEquals(expectedAttachmentString, values[14])
    }

    @Test
    fun `export multiple messages`() {
        val message1 = ExportedMessage(1L, 10L, "s1", "Sender1", 1000L, 1001L, "Body1", "incoming")
        val message2 = ExportedMessage(2L, 10L, "s2", "Sender2", 2000L, 2001L, "Body2", "outgoing")
        val csvOutput = exporter.export(listOf(message1, message2))
        val lines = csvOutput.trim().split('\n')
        assertEquals(3, lines.size) // Header + 2 data rows
        val values1 = parseCsvLine(lines[1])
        assertEquals("1", values1[0])
        val values2 = parseCsvLine(lines[2])
        assertEquals("2", values2[0])
    }

    @Test
    fun `export message with special characters in body and name`() {
        val message = ExportedMessage(
            id = 3L, threadId = 30L, senderId = "s789",
            senderName = "User, \"The Escaper\"", // Contains comma and quotes
            dateSent = 1672000000000L, dateReceived = 1672000001000L,
            body = "Line one\nLine two with \"quotes\", and a comma.", // Contains newline, quotes, comma
            messageType = "incoming"
        )
        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        assertEquals(2, lines.size)
        val values = parseCsvLine(lines[1])

        assertEquals("User, \"The Escaper\"", values[3]) // senderName
        // CsvChatExporter's escapeCsvField currently replaces " with "" but doesn't handle newlines in a way
        // that makes them part of a single CSV field if the field itself isn't quoted by the parser.
        // The current simple parser also doesn't handle newlines within fields.
        // For this test, we'll assert the escaped version, which should be on one line.
        assertEquals("Line one\nLine two with \"\"quotes\"\", and a comma.", values[6].replace("\"\"", "\"")) // body
    }

    @Test
    fun `export message with attachment filename containing special characters`() {
        val attachment = ExportedAttachment("text/plain", "file with, \"quotes\" & spaces.txt", "uri", null, 100L)
        val message = ExportedMessage(1L,1L,"s1",null,1,1,"Body","t1", listOf(attachment))

        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        val values = parseCsvLine(lines[1])

        //The CsvChatExporter's escapeCsvField method replaces " with "".
        //The attachment string joins these escaped fields with |
        assertEquals("text/plain|file with, \"\"quotes\"\" & spaces.txt|uri|100|false|false", values[14])
    }

    @Test
    fun `export message with null optional fields explicitly`() {
        val message = ExportedMessage(
            id = 4L, threadId = 40L, senderId = "sNull",
            dateSent = 1673000000000L, dateReceived = 1673000001000L,
            messageType = "system",
            senderName = null, body = null, attachments = emptyList(), quote = null
        )
        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        val values = parseCsvLine(lines[1])

        assertEquals("", values[3]) // senderName
        assertEquals("", values[6]) // body
        assertEquals("", values[10]) // quoteAuthorId
        assertEquals("", values[14]) // attachments
    }

    @Test
    fun `export message with empty string fields`() {
        val message = ExportedMessage(
            id = 5L, threadId = 50L, senderId = "sEmpty",
            dateSent = 1674000000000L, dateReceived = 1674000001000L,
            messageType = "system",
            senderName = "", body = ""
        )
        val csvOutput = exporter.export(listOf(message))
        val lines = csvOutput.trim().split('\n')
        val values = parseCsvLine(lines[1])

        // Empty strings are output as "" by escapeCsvField if they are not null
        assertEquals("", values[3])
        assertEquals("", values[6])
    }

    @Test
    fun `getFileExtension should return csv`() {
        assertEquals("csv", exporter.getFileExtension())
    }
}
