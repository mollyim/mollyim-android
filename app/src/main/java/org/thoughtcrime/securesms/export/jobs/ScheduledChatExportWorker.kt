package org.thoughtcrime.securesms.export.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

/**
 * A WorkManager worker to perform scheduled chat exports.
 */
class ScheduledChatExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
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

        val chatExportHelper = ChatExportHelper(applicationContext)
        val messages = chatExportHelper.getMessagesForThread(threadId)

        if (messages.isEmpty()) {
            Log.i(TAG, "No messages to export for thread $threadId.")
            // Optionally notify user that there was nothing to export for this schedule
            return Result.success() // Success, as there's no work to do.
        }

        val exporter: ChatExporter = when (exportFormat) {
            ExportFormat.JSON -> JsonChatExporter()
            ExportFormat.CSV -> CsvChatExporter()
        }
        val content = exporter.export(messages)
        val fileBaseName = "ScheduledExport_${chatName.replace("[^a-zA-Z0-9.-]", "_")}"

        return try {
            when (exportDestination) {
                ExportDestination.LOCAL_FILE -> {
                    val filePath = chatExportHelper.saveExportToFile(content, fileBaseName, exporter.getFileExtension())
                    if (filePath != null) {
                        Log.i(TAG, "Scheduled export saved to: $filePath")
                        showNotification("Export Successful", "$chatName exported to $filePath")
                        Result.success()
                    } else {
                        Log.e(TAG, "Failed to save scheduled export to file for thread $threadId.")
                        showNotification("Export Failed", "Could not save $chatName export.")
                        Result.failure()
                    }
                }
                ExportDestination.API_ENDPOINT -> {
                    if (apiUrl.isNullOrBlank()) {
                        Log.e(TAG, "API URL is blank for scheduled export of thread $threadId.")
                        showNotification("Export Failed", "$chatName export failed: API URL missing.")
                        Result.failure()
                    } else {
                        // Using a synchronous call here for simplicity within a CoroutineWorker.
                        // For a real implementation, you might prefer the async version or ensure
                        // the API client handles its own threading appropriately if it's blocking.
                        var success = false
                        var message = "API Export pending"

                        // sendExportToApi is async, so we need a way to wait for its result in a coroutine.
                        // This is a simplified way. Proper way might involve Kotlin Coroutines' CompletableDeferred.
                        // For now, this will likely not work as expected without further changes to sendExportToApi
                        // or using a blocking HTTP call if the client supports it.
                        // For this step, we'll assume the helper method is adapted or a blocking alternative exists.

                        // TODO: Adapt sendExportToApi or use a different mechanism for synchronous-like behavior in worker.
                        // For now, let's simulate a direct call for structure, acknowledging this part needs refinement.
                        Log.w(TAG, "API export in ScheduledChatExportWorker needs refinement for async operations. Simulating failure.")
                        // Simulate a placeholder result as the current sendExportToApi is async with callback
                        // In a real implementation, one would use a suspending HTTP client call or
                        // convert the callback to a suspendCancellableCoroutine.
                        success = false
                        message = "API export mechanism in worker needs to be made synchronous or use coroutine synchronization to work correctly."


                        if (success) {
                            Log.i(TAG, "Scheduled API export successful for thread $threadId to $apiUrl.")
                            showNotification("Export Successful", "$chatName exported to API.")
                            Result.success()
                        } else {
                            Log.e(TAG, "Scheduled API export failed for thread $threadId: $message")
                            showNotification("Export Failed", "$chatName API export failed: $message")
                            Result.failure()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during scheduled export for thread $threadId", e)
            showNotification("Export Error", "$chatName export encountered an error: ${e.message}")
            Result.failure()
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
