package org.thoughtcrime.securesms.export

import android.content.Context
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable // Added
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase // Will still be used for Recipient.resolved if not injecting that
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.export.model.ExportedAttachment
import org.thoughtcrime.securesms.export.model.ExportedMessage
import org.thoughtcrime.securesms.export.model.ExportedQuote
import org.thoughtcrime.securesms.export.model.ExportResult
import org.thoughtcrime.securesms.export.model.ExportErrorType
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
// OkHttpClient import was already here
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * Helper class to fetch and map chat data for export.
 */
class ChatExportHelper(
    private val context: Context,
    private val messageTable: MessageTable,
    private val attachmentTable: AttachmentTable, // Injected, though usage in getMessagesForThread is indirect via MessageRecord
    private val okHttpClient: OkHttpClient // Injected OkHttpClient
) {

    fun getMessagesForThread(threadId: Long): ExportResult<List<ExportedMessage>, ExportErrorType.DatabaseError> {
        return try {
            val exportedMessages = mutableListOf<ExportedMessage>()
            // Use injected messageTable directly
            val messagesInDbOrder = mutableListOf<MessageRecord>()
            messageTable.getConversation(threadId).use { reader ->
                for (messageRecord in reader) {
                    messagesInDbOrder.add(messageRecord)
                }
            }
            messagesInDbOrder.reverse()

            for (record in messagesInDbOrder) {
                val sender = Recipient.resolved(record.fromRecipient.id)
                val attachments = mutableListOf<ExportedAttachment>()

                if (record is MmsMessageRecord && record.slideDeck.slides.isNotEmpty()) {
                    record.slideDeck.slides.forEach { slide ->
                        val localUri = slide.uri?.toString() ?: slide.transferProgressUri?.toString()
                        val fileName = slide.fileName ?: MediaUtil.getFileName(context, slide.uri)
                        attachments.add(
                            ExportedAttachment(
                                contentType = slide.contentType,
                                fileName = fileName,
                                localUri = localUri,
                                downloadUrl = if (localUri == null) slide.downloadUrl else null,
                                size = slide.size,
                                isVoiceNote = slide.isVoiceNote,
                                isSticker = slide is StickerSlide
                            )
                        )
                    }
                } else if (record.attachments.isNotEmpty()) {
                    record.attachments.forEach { attachment ->
                        val dbAttachment = attachment as? DatabaseAttachment
                        attachments.add(
                            ExportedAttachment(
                                contentType = dbAttachment?.contentType ?: "application/octet-stream",
                                fileName = dbAttachment?.fileName,
                                localUri = dbAttachment?.uri?.toString(),
                                downloadUrl = null,
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
                        authorId = quoteAuthor.getPreferredExportIdentifier(), // Conceptual method
                        authorName = quoteAuthor.getDisplayName(context),
                        text = dbQuote.text?.toString(),
                        originalTimestamp = dbQuote.id
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
                    else -> "system"
                }

                exportedMessages.add(
                    ExportedMessage(
                        id = record.id,
                        threadId = record.threadId,
                        senderId = sender.getPreferredExportIdentifier(), // Conceptual method
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
            // Return success even if the list is empty, as per current decision.
            ExportResult.Success(exportedMessages)
        } catch (e: Exception) {
            // Log.e(TAG, "Error fetching messages for thread $threadId", e) // Assuming TAG is available or passed
            ExportResult.Error(
                ExportErrorType.DatabaseError(
                    userMessage = "Failed to retrieve messages from the database.",
                    logMessage = "Database error fetching messages for thread $threadId: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Saves the exported data to a file in the app's external files directory.
     */
    fun saveExportToFile(
        content: String,
        baseFileName: String, // e.g., "ChatExport_JohnDoe"
        fileExtension: String // e.g., "json" or "csv"
    ): ExportResult<String, ExportErrorType> {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return ExportResult.Error(ExportErrorType.StorageUnavailable)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${baseFileName}_${timeStamp}.$fileExtension"

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            return ExportResult.Error(
                ExportErrorType.FileSystemError(
                    userMessage = "Downloads directory is not accessible.",
                    logMessage = "Failed to access external files directory (Downloads)."
                )
            )
        }

        if (!downloadsDir.exists()) {
            try {
                downloadsDir.mkdirs()
            } catch (se: SecurityException) {
                 return ExportResult.Error(
                    ExportErrorType.FileSystemError(
                        userMessage = "Could not create downloads directory due to security restrictions.",
                        logMessage = "Failed to create downloads directory: ${se.message}",
                        cause = se
                    )
                )
            }
        }

        val file = File(downloadsDir, fileName)

        return try {
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            ExportResult.Success(file.absolutePath)
        } catch (e: IOException) {
            ExportResult.Error(
                ExportErrorType.FileSystemError(
                    userMessage = "Failed to save the export file.",
                    logMessage = "IOException saving export to file ${file.absolutePath}: ${e.message}",
                    cause = e
                )
            )
        } catch (se: SecurityException) {
             return ExportResult.Error(
                ExportErrorType.FileSystemError(
                    userMessage = "Failed to save the export file due to security restrictions.",
                    logMessage = "SecurityException saving export to file ${file.absolutePath}: ${se.message}",
                    cause = se
                )
            )
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

    /**
     * Sends the exported JSON data to a specified API endpoint, designed for use in coroutines.
     *
     * @param jsonContent The JSON string to send.
     * @param apiUrl The URL of the API endpoint.
     * @return ExportResult<String, ExportErrorType> where String is a success message.
     * @throws IOException if the underlying HTTP call fails at the network level before a response is received.
     */
    suspend fun sendExportToApiSuspending(
        jsonContent: String,
        apiUrl: String
    ): ExportResult<String, ExportErrorType> = suspendCancellableCoroutine { continuation ->
        // It's good practice to have a shared OkHttpClient instance.
        // If the app has a dependency injection framework or a singleton provider for OkHttpClient,
        // it should be used here instead of creating a new instance each time.
        // Use the injected okHttpClient

        val requestBody = jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            // TODO: Add configurable headers, including Authorization, if needed.
            // .addHeader("Authorization", "Bearer YOUR_TOKEN")
            .build()

        val call = this.okHttpClient.newCall(request) // Use injected client

        // Handle cancellation of the coroutine by cancelling the OkHttp call.
        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    // Resume with an exception, this will be caught by the try-catch in the calling coroutine (ScheduledChatExportWorker)
                    // and translated into ExportResult.Error(ExportErrorType.NetworkError(...)) there.
                    continuation.resumeWithException(IOException("Network request failed for $apiUrl: ${e.message}", e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { // Ensures the response body is closed to prevent resource leaks.
                    if (it.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resume(ExportResult.Success("Export successful. Status: ${it.code}"))
                        }
                    } else {
                        val responseBodyString = it.body?.string() ?: "No response body"
                        if (continuation.isActive) {
                            continuation.resume(
                                ExportResult.Error(
                                    ExportErrorType.ApiError(
                                        statusCode = it.code,
                                        userMessage = "API request failed. Please check the server.",
                                        logMessage = "API export failed for URL $apiUrl. Status: ${it.code}, Body: $responseBodyString",
                                        errorBody = responseBodyString
                                    )
                                )
                            )
                        }
                    }
                }
            }
        })
    }
}
