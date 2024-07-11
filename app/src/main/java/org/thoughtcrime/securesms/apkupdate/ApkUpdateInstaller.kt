/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.apkupdate

import android.content.Context
import android.net.Uri
import org.signal.core.util.getDownloadManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FileUtils
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

object ApkUpdateInstaller {

  private val TAG = Log.tag(ApkUpdateInstaller::class.java)

  /**
   * Installs the downloaded APK silently if possible. If not, prompts the user with a notification to install.
   * May show errors instead under certain conditions.
   *
   * A common pattern you may see is that this is called with [userInitiated] = false (or some other state
   * that prevents us from auto-updating, like the app being in the foreground), causing this function
   * to show an install prompt notification. The user clicks that notification, calling this with
   * [userInitiated] = true, and then everything installs.
   */
  fun installOrPromptForInstall(context: Context, downloadId: Long, userInitiated: Boolean) {
    if (downloadId != SignalStore.apkUpdate.downloadId) {
      Log.w(TAG, "DownloadId doesn't match the one we're waiting for (current: $downloadId, expected: ${SignalStore.apkUpdate.downloadId})! We likely have newer data. Ignoring.")
      ApkUpdateNotifications.dismissInstallPrompt(context)
      AppDependencies.jobManager.add(ApkUpdateJob())
      return
    }

    val digest = SignalStore.apkUpdate.digest
    if (digest == null) {
      Log.w(TAG, "DownloadId matches, but digest is null! Inconsistent state. Failing and clearing state.")
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
      return
    }

    if (!isMatchingDigest(context, downloadId, digest)) {
      Log.w(TAG, "DownloadId matches, but digest does not! Bad download or inconsistent state. Failing and clearing state.")
      SignalStore.apkUpdate.clearDownloadAttributes()
      ApkUpdateNotifications.showInstallFailed(context, ApkUpdateNotifications.FailureReason.UNKNOWN)
      return
    }

    val apkUri = getDownloadedApkUri(context, downloadId)
    if (apkUri == null) {
      Log.w(TAG, "Could not get download APK URI!")
      return
    }

    ApkUpdateNotifications.showInstallPrompt(context, apkUri)
  }

  private fun getDownloadedApkUri(context: Context, downloadId: Long): Uri? {
    return try {
      context.getDownloadManager().getUriForDownloadedFile(downloadId)
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
    }
  }

  private fun isMatchingDigest(context: Context, downloadId: Long, expectedDigest: ByteArray): Boolean {
    return try {
      FileInputStream(context.getDownloadManager().openDownloadedFile(downloadId).fileDescriptor).use { stream ->
        val digest = FileUtils.getFileDigest(stream)
        MessageDigest.isEqual(digest, expectedDigest)
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
      false
    }
  }
}
