package org.thoughtcrime.securesms.export.formatter

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.thoughtcrime.securesms.export.model.ExportedAttachment
import org.thoughtcrime.securesms.export.model.ExportedMessage
import org.thoughtcrime.securesms.export.model.ExportedQuote

class JsonChatExporterTest {

    private lateinit var exporter: JsonChatExporter

    @Before
    fun setUp() {
        exporter = JsonChatExporter()
    }

    @Test
    fun `export empty list should produce empty JSON array`() {
        val messages = emptyList<ExportedMessage>()
        val jsonOutput = exporter.export(messages)
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(0, jsonArray.length())
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
        val jsonOutput = exporter.export(listOf(message))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)

        assertEquals(1L, jsonObj.getLong("id"))
        assertEquals(10L, jsonObj.getLong("threadId"))
        assertEquals("sender123", jsonObj.getString("senderId"))
        assertTrue(jsonObj.isNull("senderName")) // Check for null explicitly
        assertEquals(1670000000000L, jsonObj.getLong("dateSent"))
        assertEquals(1670000001000L, jsonObj.getLong("dateReceived"))
        assertTrue(jsonObj.isNull("body"))
        assertEquals("incoming", jsonObj.getString("messageType"))
        assertEquals(0, jsonObj.getJSONArray("attachments").length())
        assertTrue(jsonObj.isNull("quote"))
        assertFalse(jsonObj.getBoolean("isViewOnce"))
        assertFalse(jsonObj.getBoolean("isRemoteDelete"))
    }

    @Test
    fun `export single message with all data`() {
        val attachment = ExportedAttachment("image/jpeg", "photo.jpg", "content://uri/1", null, 12345L, false, false)
        val quote = ExportedQuote("quoteAuthor456", "Quote Author", "This was the original message", 1669000000000L)
        val message = ExportedMessage(
            id = 2L,
            threadId = 20L,
            senderId = "sender456",
            senderName = "Sender Name",
            dateSent = 1671000000000L,
            dateReceived = 1671000001000L,
            body = "Hello, world!",
            messageType = "outgoing",
            attachments = listOf(attachment),
            quote = quote,
            isViewOnce = true,
            isRemoteDelete = true
        )
        val jsonOutput = exporter.export(listOf(message))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)

        assertEquals(2L, jsonObj.getLong("id"))
        assertEquals("Hello, world!", jsonObj.getString("body"))
        assertEquals("Sender Name", jsonObj.getString("senderName"))
        assertTrue(jsonObj.getBoolean("isViewOnce"))
        assertTrue(jsonObj.getBoolean("isRemoteDelete"))

        val attachmentsArray = jsonObj.getJSONArray("attachments")
        assertEquals(1, attachmentsArray.length())
        val attachmentObj = attachmentsArray.getJSONObject(0)
        assertEquals("image/jpeg", attachmentObj.getString("contentType"))
        assertEquals("photo.jpg", attachmentObj.getString("fileName"))
        assertEquals("content://uri/1", attachmentObj.getString("localUri"))
        assertTrue(attachmentObj.isNull("downloadUrl"))
        assertEquals(12345L, attachmentObj.getLong("size"))

        val quoteObj = jsonObj.getJSONObject("quote")
        assertEquals("quoteAuthor456", quoteObj.getString("authorId"))
        assertEquals("Quote Author", quoteObj.getString("authorName"))
        assertEquals("This was the original message", quoteObj.getString("text"))
        assertEquals(1669000000000L, quoteObj.getLong("originalTimestamp"))
    }

    @Test
    fun `export multiple messages`() {
        val message1 = ExportedMessage(1L, 10L, "s1", "Sender1", 1000L, 1001L, "Body1", "incoming")
        val message2 = ExportedMessage(2L, 10L, "s2", "Sender2", 2000L, 2001L, "Body2", "outgoing")
        val jsonOutput = exporter.export(listOf(message1, message2))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(2, jsonArray.length())
        assertEquals(1L, jsonArray.getJSONObject(0).getLong("id"))
        assertEquals(2L, jsonArray.getJSONObject(1).getLong("id"))
    }

    @Test
    fun `export message with special characters in body`() {
        val message = ExportedMessage(
            id = 3L, threadId = 30L, senderId = "s789", senderName = "Test User \"The Escaper\"",
            dateSent = 1672000000000L, dateReceived = 1672000001000L,
            body = "Line one\nLine two with \"quotes\" and backslash \\ and unicode \uD83D\uDE00 smiley.",
            messageType = "incoming"
        )
        val jsonOutput = exporter.export(listOf(message))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)
        assertEquals("Line one\nLine two with \"quotes\" and backslash \\ and unicode \uD83D\uDE00 smiley.", jsonObj.getString("body"))
        assertEquals("Test User \"The Escaper\"", jsonObj.getString("senderName"))

        // Gson handles JSON escaping, so we expect the literal characters in the parsed string
        // For example, a newline character \n in the input string should be \\n in the JSON string,
        // and when parsed by org.json, it becomes \n again in the Java/Kotlin string.
        // The Gson library used by JsonChatExporter should handle this correctly.
        // We are asserting the final parsed string matches the input string.
        assertEquals("Line one\nLine two with \"quotes\" and backslash \\ and unicode \uD83D\uDE00 smiley.", message.body)
        assertEquals(message.body, jsonObj.getString("body"))

    }

    @Test
    fun `export message with null optional fields`() {
        val message = ExportedMessage(
            id = 4L, threadId = 40L, senderId = "sNull",
            dateSent = 1673000000000L, dateReceived = 1673000001000L,
            messageType = "system",
            senderName = null, // Explicitly null
            body = null,       // Explicitly null
            attachments = emptyList(),
            quote = null
        )
        val jsonOutput = exporter.export(listOf(message))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)

        assertTrue(jsonObj.isNull("senderName"))
        assertTrue(jsonObj.isNull("body"))
        assertTrue(jsonObj.getJSONArray("attachments").length() == 0)
        assertTrue(jsonObj.isNull("quote"))
    }

    @Test
    fun `export message with empty string fields`() {
        val message = ExportedMessage(
            id = 5L, threadId = 50L, senderId = "sEmpty",
            dateSent = 1674000000000L, dateReceived = 1674000001000L,
            messageType = "system",
            senderName = "", // Empty string
            body = ""        // Empty string
        )
        val jsonOutput = exporter.export(listOf(message))
        val jsonArray = JSONArray(jsonOutput)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)

        assertEquals("", jsonObj.getString("senderName"))
        assertEquals("", jsonObj.getString("body"))
    }


    @Test
    fun `getFileExtension should return json`() {
        assertEquals("json", exporter.getFileExtension())
    }
}
