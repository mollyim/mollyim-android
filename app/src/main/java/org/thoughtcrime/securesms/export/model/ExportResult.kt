package org.thoughtcrime.securesms.export.model

sealed class ExportResult<out S, out E> {
    data class Success<out S>(val data: S) : ExportResult<S, Nothing>()
    data class Error<out E>(val error: E) : ExportResult<Nothing, E>()
}

sealed class ExportErrorType {
    // File Export Errors
    data class FileSystemError(val userMessage: String, val logMessage: String, val cause: Throwable? = null) : ExportErrorType()
    object StorageUnavailable : ExportErrorType()

    // API Export Errors
    data class ApiError(val statusCode: Int?, val userMessage: String, val logMessage: String, val errorBody: String? = null) : ExportErrorType()
    data class NetworkError(val userMessage: String, val logMessage: String, val cause: Throwable? = null) : ExportErrorType()
    object ApiUrlMissing : ExportErrorType()

    // Data Fetching Errors
    data class DatabaseError(val userMessage: String, val logMessage: String, val cause: Throwable? = null) : ExportErrorType()
    object NoMessagesToExport : ExportErrorType() // Represents an empty export, not necessarily a failure.

    // General/Unknown
    data class UnknownError(val userMessage: String, val logMessage: String, val cause: Throwable? = null) : ExportErrorType()
}
