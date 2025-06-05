package org.thoughtcrime.securesms.export

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager // Added for injection
import com.google.gson.Gson // Added for injection
import com.google.gson.reflect.TypeToken
// import org.thoughtcrime.securesms.database.SignalDatabase // No longer needed for KVD
import org.thoughtcrime.securesms.database.KeyValueDatabase // Added for injection
import org.thoughtcrime.securesms.export.jobs.ScheduledChatExportWorker
import org.thoughtcrime.securesms.export.model.PersistedScheduleData
import org.thoughtcrime.securesms.export.ui.ExportFrequency
import java.util.UUID // Keep for now, though uniqueWorkName is deterministic
import java.util.concurrent.TimeUnit

/**
 * Manages the scheduling of chat exports using WorkManager.
 */
class ChatExportScheduler(
    private val context: Context, // Context might still be needed for other things or future use
    private val workManager: WorkManager,
    private val keyValueDatabase: KeyValueDatabase,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "ChatExportScheduler"
        private const val KVD_SCHEDULE_LIST_KEY = "chat_export_schedule_ids"
        private const val KVD_SCHEDULE_PREFIX = "chat_export_schedule_"
    }

    // Removed lazy initializers for keyValueDatabase and gson

    fun scheduleExport(
        threadId: Long,
        chatName: String,
        format: String, // From ExportFormat.name()
        destination: String, // From ExportDestination.name()
        apiUrl: String?,
        frequency: ExportFrequency
    ): String {
        // Use injected workManager
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
        val constraints = constraintsBuilder.build()

        val exportWorkRequest = PeriodicWorkRequestBuilder<ScheduledChatExportWorker>(
            repeatIntervalMillis, TimeUnit.MILLISECONDS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(uniqueWorkName)
            .build()

        this.workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.REPLACE,
            exportWorkRequest
        )

        val scheduleData = PersistedScheduleData(
            uniqueWorkName = uniqueWorkName,
            threadId = threadId,
            chatName = chatName,
            format = format,
            destination = destination,
            apiUrl = apiUrl,
            frequency = frequency.name // Store enum name
        )
        saveScheduleConfiguration(scheduleData)

        Log.i(TAG, "Scheduled export for thread '$chatName' ($threadId) with work name '$uniqueWorkName', frequency: $frequency")
        return uniqueWorkName
    }

    fun cancelScheduledExport(uniqueWorkName: String) {
        this.workManager.cancelUniqueWork(uniqueWorkName)
        removeScheduleConfiguration(uniqueWorkName)
        Log.i(TAG, "Cancelled scheduled export with work name '$uniqueWorkName'")
    }

    fun cancelAllScheduledExports() {
        val scheduleIds = getScheduleIds()
        if (scheduleIds.isEmpty()) {
            Log.i(TAG, "No scheduled exports to cancel.")
            return
        }
        scheduleIds.forEach { uniqueWorkName ->
            this.workManager.cancelUniqueWork(uniqueWorkName)
            this.keyValueDatabase.remove(KVD_SCHEDULE_PREFIX + uniqueWorkName)
        }
        this.keyValueDatabase.remove(KVD_SCHEDULE_LIST_KEY)
        Log.i(TAG, "Cancelled all ${scheduleIds.size} scheduled exports.")
    }

    private fun saveScheduleConfiguration(scheduleData: PersistedScheduleData) {
        val scheduleJson = this.gson.toJson(scheduleData)
        this.keyValueDatabase.put(KVD_SCHEDULE_PREFIX + scheduleData.uniqueWorkName, scheduleJson)

        val currentIds = getScheduleIds().toMutableSet()
        currentIds.add(scheduleData.uniqueWorkName)
        this.keyValueDatabase.put(KVD_SCHEDULE_LIST_KEY, this.gson.toJson(currentIds))
        Log.d(TAG, "Saved schedule config for ${scheduleData.uniqueWorkName}")
    }

    private fun removeScheduleConfiguration(uniqueWorkName: String) {
        this.keyValueDatabase.remove(KVD_SCHEDULE_PREFIX + uniqueWorkName)

        val currentIds = getScheduleIds().toMutableSet()
        if (currentIds.remove(uniqueWorkName)) {
            this.keyValueDatabase.put(KVD_SCHEDULE_LIST_KEY, this.gson.toJson(currentIds))
        }
        Log.d(TAG, "Removed schedule config for $uniqueWorkName")
    }

    fun getScheduleConfig(uniqueWorkName: String): PersistedScheduleData? {
        val scheduleJson = this.keyValueDatabase.getString(KVD_SCHEDULE_PREFIX + uniqueWorkName, null)
        return scheduleJson?.let {
            this.gson.fromJson(it, PersistedScheduleData::class.java)
        }
    }

    fun getAllScheduledExportConfigs(): List<PersistedScheduleData> {
        val scheduleIds = getScheduleIds()
        return scheduleIds.mapNotNull { id ->
            getScheduleConfig(id)
        }
    }

    private fun getScheduleIds(): Set<String> {
        val idsJson = this.keyValueDatabase.getString(KVD_SCHEDULE_LIST_KEY, null)
        return idsJson?.let {
            val type = object : TypeToken<Set<String>>() {}.type
            this.gson.fromJson<Set<String>>(it, type)
        } ?: emptySet()
    }
}
