package org.thoughtcrime.securesms.export.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Production implementation of FileSystemOps using standard Android framework calls.
 */
class AndroidFileSystemOps : FileSystemOps {

    override fun openOutputStream(file: File): OutputStream? {
        return try {
            FileOutputStream(file)
        } catch (e: IOException) {
            // Log error or handle as per application's error strategy
            null // Or throw a custom wrapped exception
        }
    }

    override fun getExternalFilesDir(context: Context, type: String?): File? {
        return context.getExternalFilesDir(type)
    }

    override fun fileExists(file: File): Boolean {
        return file.exists()
    }

    override fun mkdirs(file: File): Boolean {
        return if (file.exists()) true else file.mkdirs()
    }

    override fun getExternalStorageState(): String {
        return Environment.getExternalStorageState()
    }

    override fun createNewFile(file: File): Boolean {
        return try {
            file.createNewFile()
        } catch (e: IOException) {
            false
        }
    }

    override fun getAbsolutePath(file: File): String {
        return file.absolutePath
    }
}
