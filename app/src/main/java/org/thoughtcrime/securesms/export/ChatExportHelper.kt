package org.thoughtcrime.securesms.export

import android.content.Context
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.export.model.ExportedAttachment
import org.thoughtcrime.securesms.export.model.ExportedMessage
import org.thoughtcrime.securesms.export.model.ExportedQuote
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.StickerSlide
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
// IOException is already imported via java.io.IOException used in saveExportToFile
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * Helper class to fetch and map chat data for export.
 */
class ChatExportHelper(private val context: Context) {

    fun getMessagesForThread(threadId: Long): List<ExportedMessage> {
        val exportedMessages = mutableListOf<ExportedMessage>()
        val messageTable = SignalDatabase.messages() // Assuming direct access like this is possible
        val attachmentTable = SignalDatabase.attachments() // Assuming direct access

        // Using a cursor to iterate through all messages in a thread
        // The getConversation method typically orders messages from newest to oldest.
        // For export, chronological order (oldest to newest) is usually preferred.
        // So we will fetch all and then reverse, or adapt the query if possible.
        val messagesInDbOrder = mutableListOf<MessageRecord>()
        messageTable.getConversation(threadId).use { reader ->
            for (messageRecord in reader) {
                messagesInDbOrder.add(messageRecord)
            }
        }
        // Reverse to get chronological order
        messagesInDbOrder.reverse()


        for (record in messagesInDbOrder) {
            val sender = Recipient.resolved(record.fromRecipient.id)
            val attachments = mutableListOf<ExportedAttachment>()

            if (record is MmsMessageRecord && record.slideDeck.slides.isNotEmpty()) {
                record.slideDeck.slides.forEach { slide ->
                    // Prefer local URI if the attachment is downloaded
                    val localUri = slide.uri?.toString() ?: slide.transferProgressUri?.toString()
                    val fileName = slide.fileName ?: MediaUtil.getFileName(context, slide.uri)

                    attachments.add(
                        ExportedAttachment(
                            contentType = slide.contentType,
                            fileName = fileName,
                            localUri = localUri,
                            downloadUrl = if (localUri == null) slide.downloadUrl else null, // Only relevant if not local
                            size = slide.size,
                            isVoiceNote = slide.isVoiceNote,
                            isSticker = slide is StickerSlide
                        )
                    )
                }
            } else if (record.attachments.isNotEmpty()) { // For older attachment models if any
                 record.attachments.forEach { attachment ->
                    val dbAttachment = attachment as? DatabaseAttachment
                    attachments.add(
                        ExportedAttachment(
                            contentType = dbAttachment?.contentType ?: "application/octet-stream",
                            fileName = dbAttachment?.fileName,
                            localUri = dbAttachment?.uri?.toString(),
                            downloadUrl = null, // Older model might not have separate download URL easily
                            size = dbAttachment?.size ?: 0L,
                            isVoiceNote = dbAttachment?.isVoiceNote ?: false,
                            isSticker = dbAttachment?.isSticker ?: false
                        )
                    )
                 }
            }


            val quote: ExportedQuote? = record.quote?.let { dbQuote ->
                val quoteAuthor = Recipient.resolved(dbQuote.author)
                ExportedQuote(
                    authorId = quoteAuthor.id.toString(), // Or use e164/UUID based on availability
                    authorName = quoteAuthor.getDisplayName(context),
                    text = dbQuote.text?.toString(),
                    originalTimestamp = dbQuote.id // This 'id' in Quote is usually the original message's sent_timestamp
                )
            }

            val messageType = when {
                record.isOutgoing -> "outgoing"
                record.isIncoming -> "incoming"
                record.isJoin() -> "join"
                record.isGroupTimerChange() -> "timer_change"
                record.isGroupUpdate() -> "group_update"
                record.isGroupV2Leave() -> "group_leave"
                record.isEndSession -> "end_session"
                // Add more system message types as needed
                else -> "system"
            }

            exportedMessages.add(
                ExportedMessage(
                    id = record.id,
                    threadId = record.threadId,
                    senderId = sender.id.toString(), // Or use e164/UUID
                    senderName = sender.getDisplayName(context),
                    dateSent = record.dateSent,
                    dateReceived = record.dateReceived,
                    body = record.body?.toString(),
                    messageType = messageType,
                    attachments = attachments,
                    quote = quote,
                    isViewOnce = record.isViewOnce,
                    isRemoteDelete = record.isRemoteDelete
                )
            )
        }
        return exportedMessages
    }

    /**
     * Saves the exported data to a file in the app's external files directory (e.g., Downloads/AppName).
     * Returns the absolute path of the saved file, or null on failure.
     */
    fun saveExportToFile(
        content: String,
        baseFileName: String, // e.g., "ChatExport_JohnDoe"
        fileExtension: String // e.g., "json" or "csv"
    ): String? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            // External storage not available
            // Consider logging this or throwing an exception
            return null
        }

        // Create a timestamped filename to avoid overwrites and ensure uniqueness
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${baseFileName}_${timeStamp}.$fileExtension"

        // Get the directory for the app's public documents, or a subdirectory within Downloads
        // Using getExternalFilesDir for simplicity, which is app-specific external storage.
        // For saving to common Downloads, Storage Access Framework would be more appropriate for Android Q+
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            // Handle error: directory not accessible
            return null
        }

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            return file.absolutePath
        } catch (e: IOException) {
            // Log error e.g., Log.e(TAG, "Error saving export to file", e)
            // Consider throwing a custom exception or returning a more specific error indicator
            return null
        }
    }

    /**
     * Sends the exported JSON data to a specified API endpoint.
     * Uses a callback to handle asynchronous success or failure.
     *
     * @param jsonContent The JSON string to send.
     * @param apiUrl The URL of the API endpoint.
     * @param callback Callback to be invoked with the result of the API call.
     */
    fun sendExportToApi(
        jsonContent: String,
        apiUrl: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val client = OkHttpClient() // Consider using a shared instance if available in the app

        val requestBody = jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            // .addHeader("Authorization", "Bearer YOUR_TOKEN") // Example for auth, to be configurable
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log.e(TAG, "API export failed", e)
                callback(false, "Export failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { // Ensure the response body is closed
                    if (it.isSuccessful) {
                        // Log.i(TAG, "API export successful")
                        callback(true, "Export successful. Status: ${it.code}")
                    } else {
                        // Log.w(TAG, "API export failed with status: ${it.code}")
                        val responseBody = it.body?.string() ?: "No response body"
                        callback(false, "Export failed. Status: ${it.code}, Body: $responseBody")
                    }
                }
            }
        })
    }
}
