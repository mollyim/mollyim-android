package org.thoughtcrime.securesms.export

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.thoughtcrime.securesms.export.jobs.ScheduledChatExportWorker
import org.thoughtcrime.securesms.export.ui.ExportFrequency // Assuming this is in the ui package from previous steps
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages the scheduling of chat exports using WorkManager.
 */
class ChatExportScheduler(private val context: Context) {

    companion object {
        private const val TAG = "ChatExportScheduler"
    }

    fun scheduleExport(
        threadId: Long,
        chatName: String,
        format: String, // From ExportFormat.name()
        destination: String, // From ExportDestination.name()
        apiUrl: String?,
        frequency: ExportFrequency
    ): String {
        val workManager = WorkManager.getInstance(context.applicationContext)
        // Using threadId and a prefix for a more deterministic unique work name,
        // allowing easier updates/cancellations if the user re-schedules for the same thread.
        // However, if multiple distinct schedules for the same thread are desired, UUID is better.
        // For now, let's assume one schedule per thread for simplicity of management.
        val uniqueWorkName = "scheduled_export_thread_$threadId"

        val repeatIntervalMillis = when (frequency) {
            ExportFrequency.DAILY -> TimeUnit.DAYS.toMillis(1)
            ExportFrequency.WEEKLY -> TimeUnit.DAYS.toMillis(7)
            ExportFrequency.MONTHLY -> TimeUnit.DAYS.toMillis(30) // Approximation
        }

        val inputData = Data.Builder()
            .putLong(ScheduledChatExportWorker.KEY_THREAD_ID, threadId)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_FORMAT, format)
            .putString(ScheduledChatExportWorker.KEY_EXPORT_DESTINATION, destination)
            .putString(ScheduledChatExportWorker.KEY_CHAT_NAME, chatName)
            .apply { apiUrl?.let { putString(ScheduledChatExportWorker.KEY_API_URL, it) } }
            .build()

        val constraintsBuilder = Constraints.Builder()
        if (destination == "API_ENDPOINT") { // Assuming "API_ENDPOINT" is the enum name string
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        // Future: .setRequiresBatteryNotLow(true)
        // Future: .setRequiresStorageNotLow(true)
        val constraints = constraintsBuilder.build()

        val exportWorkRequest = PeriodicWorkRequestBuilder<ScheduledChatExportWorker>(
            repeatIntervalMillis, TimeUnit.MILLISECONDS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(uniqueWorkName) // Tag can be used for querying status or cancelling
            .build()

        // REPLACE policy means if a schedule with this name exists, it's updated.
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.REPLACE,
            exportWorkRequest
        )

        // TODO: Persist the details of this schedule (uniqueWorkName, threadId, chatName, format, destination, apiUrl, frequency)
        // in SharedPreferences or KeyValueDatabase to allow users to view and manage their schedules.
        Log.i(TAG, "Scheduled export for thread '$chatName' ($threadId) with work name '$uniqueWorkName', frequency: $frequency")
        return uniqueWorkName // Return the ID for potential management
    }

    fun cancelScheduledExport(uniqueWorkName: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName)
        // TODO: Remove the schedule from persistent storage as well.
        Log.i(TAG, "Cancelled scheduled export with work name '$uniqueWorkName'")
    }

    fun cancelAllScheduledExports() {
        // This would typically involve retrieving all stored uniqueWorkNames and cancelling them.
        // For now, just a placeholder.
        // Or, if using a common tag for all export jobs:
        // WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag("CHAT_EXPORT_SCHEDULE_TAG")
        Log.w(TAG, "cancelAllScheduledExports - Implementation needed to fetch and cancel all known jobs.")
    }

    // TODO: Add methods to retrieve stored schedule configurations for UI display and management.
    // fun getAllScheduledExports(): List<PersistedScheduleData> { ... }
}

// Data class for persisting schedule info (example)
// data class PersistedScheduleData(
//    val uniqueWorkName: String,
//    val threadId: Long,
//    val chatName: String,
//    val format: String,
//    val destination: String,
//    val apiUrl: String?,
//    val frequency: String // Store enum name
// )
