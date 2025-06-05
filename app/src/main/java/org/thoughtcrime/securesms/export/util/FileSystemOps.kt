package org.thoughtcrime.securesms.export.util

import android.content.Context
import java.io.File
import java.io.OutputStream // Changed from java.io.FileOutputStream to java.io.OutputStream for interface flexibility

/**
 * Interface for abstracting file system operations to improve testability.
 */
interface FileSystemOps {
    fun openOutputStream(file: File): OutputStream? // Can return null if open fails before exception
    fun getExternalFilesDir(context: Context, type: String?): File?
    fun fileExists(file: File): Boolean
    fun mkdirs(file: File): Boolean
    fun getExternalStorageState(): String // No context needed for Environment.getExternalStorageState()
    fun createNewFile(file: File): Boolean // Added for completeness if needed by saveExportToFile
    fun getAbsolutePath(file: File): String // Helper to get file.absolutePath
}
