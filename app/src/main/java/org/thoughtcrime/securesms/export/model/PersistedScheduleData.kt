package org.thoughtcrime.securesms.export.model

data class PersistedScheduleData(
    val uniqueWorkName: String,
    val threadId: Long,
    val chatName: String,
    val format: String, // ExportFormat enum name
    val destination: String, // ExportDestination enum name
    val apiUrl: String?,
    val frequency: String // ExportFrequency enum name
)
