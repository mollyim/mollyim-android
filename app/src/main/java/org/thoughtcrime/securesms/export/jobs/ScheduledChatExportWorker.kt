package org.thoughtcrime.securesms.export.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
// Import ChatExportHelper
import org.thoughtcrime.securesms.export.ChatExportHelper
import org.thoughtcrime.securesms.export.formatter.ChatExporter
import org.thoughtcrime.securesms.export.formatter.CsvChatExporter
import org.thoughtcrime.securesms.export.formatter.JsonChatExporter
import org.thoughtcrime.securesms.export.ui.ExportDestination
import org.thoughtcrime.securesms.export.ui.ExportFormat
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationFactory
import org.thoughtcrime.securesms.notifications.NotificationIds
// import org.thoughtcrime.securesms.util.TextSecurePreferences // For potential notification settings - R class might be an issue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException
import org.thoughtcrime.securesms.export.model.ExportResult // Added
import org.thoughtcrime.securesms.export.model.ExportErrorType // Added

/**
 * A WorkManager worker to perform scheduled chat exports.
 */
class ScheduledChatExportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val chatExportHelper: ChatExportHelper // Injected ChatExportHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ScheduledChatExportWorker"
        const val KEY_THREAD_ID = "thread_id"
        const val KEY_EXPORT_FORMAT = "export_format" // "JSON" or "CSV"
        const val KEY_EXPORT_DESTINATION = "export_destination" // "LOCAL_FILE" or "API_ENDPOINT"
        const val KEY_API_URL = "api_url" // Only if destination is API_ENDPOINT
        const val KEY_CHAT_NAME = "chat_name" // For notifications
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Scheduled chat export worker started.")

        val threadId = inputData.getLong(KEY_THREAD_ID, -1L)
        val exportFormatString = inputData.getString(KEY_EXPORT_FORMAT)
        val exportDestinationString = inputData.getString(KEY_EXPORT_DESTINATION)
        val apiUrl = inputData.getString(KEY_API_URL)
        val chatName = inputData.getString(KEY_CHAT_NAME) ?: "Chat"

        if (threadId == -1L || exportFormatString == null || exportDestinationString == null) {
            Log.e(TAG, "Missing required input data (threadId, format, or destination).")
            return Result.failure()
        }

        val exportFormat = ExportFormat.valueOf(exportFormatString)
        val exportDestination = ExportDestination.valueOf(exportDestinationString)

        // Remove local instantiation, use injected this.chatExportHelper
        // val chatExportHelper = ChatExportHelper(applicationContext, SignalDatabase.messages(), SignalDatabase.attachments(), OkHttpClient())

        val messagesResult = this.chatExportHelper.getMessagesForThread(threadId)
        val messages: List<org.thoughtcrime.securesms.export.model.ExportedMessage> = when (messagesResult) {
            is ExportResult.Success -> messagesResult.data
            is ExportResult.Error -> {
                Log.e(TAG, "Failed to get messages for thread $threadId: ${messagesResult.error.logMessage}")
                showNotification("Export Failed", "Could not retrieve messages for $chatName: ${messagesResult.error.userMessage}")
                return Result.failure()
            }
        }

        if (messages.isEmpty()) {
            Log.i(TAG, "No messages to export for thread $threadId. Worker completing successfully.")
            // Optionally notify user, or just complete silently.
            // showNotification("Export Notice", "No new messages to export for $chatName.")
            return Result.success() // Success, as there's no work to do.
        }

        val exporter: ChatExporter = when (exportFormat) {
            ExportFormat.JSON -> JsonChatExporter()
            ExportFormat.CSV -> CsvChatExporter()
        }
        val content = exporter.export(messages)
        val fileBaseName = "ScheduledExport_${chatName.replace("[^a-zA-Z0-9.-]", "_")}"

        return when (exportDestination) {
            ExportDestination.LOCAL_FILE -> {
                when (val saveResult = this.chatExportHelper.saveExportToFile(content, fileBaseName, exporter.getFileExtension())) {
                    is ExportResult.Success -> {
                        Log.i(TAG, "Scheduled export saved to: ${saveResult.data}")
                        showNotification("Export Successful", "$chatName exported to ${saveResult.data}")
                        Result.success()
                    }
                    is ExportResult.Error -> {
                        Log.e(TAG, "Failed to save scheduled export to file for thread $threadId: ${saveResult.error.logMessage}")
                        showNotification("Export Failed", "Could not save $chatName export: ${saveResult.error.userMessage}")
                        Result.failure()
                    }
                }
            }
            ExportDestination.API_ENDPOINT -> {
                if (apiUrl.isNullOrBlank()) {
                    Log.e(TAG, "API URL is blank for scheduled export of thread $threadId.")
                    val error = ExportErrorType.ApiUrlMissing
                    showNotification("Export Failed", "$chatName export failed: API URL missing.")
                    Result.failure()
                } else {
                    try {
                        when (val apiResult = this.chatExportHelper.sendExportToApiSuspending(content, apiUrl)) {
                            is ExportResult.Success -> {
                                Log.i(TAG, "Scheduled API export successful for thread $threadId to $apiUrl. Message: ${apiResult.data}")
                                showNotification("Export Successful", "$chatName exported to API: ${apiResult.data}")
                                Result.success()
                            }
                            is ExportResult.Error -> {
                                val error = apiResult.error
                                Log.e(TAG, "Scheduled API export failed for thread $threadId: ${error.logMessage}")
                                showNotification("Export Failed", "$chatName API export failed: ${error.userMessage}")
                                Result.failure()
                            }
                        }
                    } catch (e: IOException) { // Catch network errors from sendExportToApiSuspending
                        val error = ExportErrorType.NetworkError(
                            userMessage = "Network error during API export. Please check your connection.",
                            logMessage = "IOException during API export for thread $threadId to $apiUrl: ${e.message}",
                            cause = e
                        )
                        Log.e(TAG, error.logMessage, e)
                        showNotification("Export Failed", error.userMessage)
                        Result.failure()
                    } catch (e: Exception) { // Catch any other unexpected errors from the call itself
                        val error = ExportErrorType.UnknownError(
                            userMessage = "An unexpected error occurred during API export.",
                            logMessage = "Unexpected exception during API export for thread $threadId: ${e.message}",
                            cause = e
                        )
                        Log.e(TAG, error.logMessage, e)
                        showNotification("Export Failed", error.userMessage)
                        Result.failure()
                    }
                }
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        // Basic notification; in a real app, use a more robust notification builder
        // and consider user preferences for notifications.
        // Assuming NotificationChannels and NotificationFactory are available and setup
        // The R.drawable.ic_notification would need to be a valid resource. Using 0 as placeholder.
        // if (NotificationChannels.isNotificationChannelEnabled(applicationContext, NotificationChannels.OTHER)) {
        //      val notification = NotificationFactory.createBasicNotification(
        //         applicationContext,
        //         title,
        //         message,
        //         NotificationIds.JOB_MANAGER_FOREGROUND, // Potentially use a new ID
        //         NotificationChannels.OTHER, // Or a new dedicated channel for exports
        //         0 // icon res id - placeholder for R.drawable.ic_notification
        //     )
        //     NotificationFactory.getNotificationManager(applicationContext).notify(System.currentTimeMillis().toInt(), notification)
        // }
        Log.d(TAG, "Notification Placeholder: Title: $title, Message: $message")
    }
}
