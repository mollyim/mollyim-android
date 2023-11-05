package org.thoughtcrime.securesms.jobs

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.OkHttpClient
import okhttp3.Request
import org.signal.core.util.Hex
import org.signal.core.util.forEach
import org.signal.core.util.getDownloadManager
import org.signal.core.util.logging.Log
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.apkupdate.ApkUpdateDownloadManagerReceiver
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.Network
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.FileUtils
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Designed to be a periodic job that checks for new app updates when the user is running a build that
 * is distributed outside of the play store (like our website build).
 *
 * It uses the DownloadManager to actually download the APK for some easy reliability, considering the
 * file it's downloading it rather large (70+ MB).
 */
class ApkUpdateJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "UpdateApkJob"
    private val TAG = Log.tag(ApkUpdateJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(2)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(2)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(IOException::class)
  public override fun onRun() {
    if (!FeatureFlags.selfUpdater() || !TextSecurePreferences.isUpdateApkEnabled(context)) {
      Log.i(TAG, "In-app updater disabled! Exiting.")
      return
    }

    val includeBeta = TextSecurePreferences.isUpdateApkIncludeBetaEnabled(context)

    Log.i(TAG, "Checking for APK update [stable" + (if (includeBeta) ", beta" else "") + "]...")

    val client = OkHttpClient().newBuilder()
      .socketFactory(Network.socketFactory)
      .proxySelector(Network.proxySelectorForSocks)
      .dns(Network.dns)
      .build()

    val request = Request.Builder().url("${BuildConfig.FDROID_UPDATE_URL}/index-v1.json").build()

    val responseBody: String = client.newCall(request).execute().use { response ->
      if (!response.isSuccessful || response.body() == null) {
        throw IOException("Failed to fetch updates from fdroid repo")
      }
      response.body()!!.string()
    }

    val repoIndex: RepoIndex = JsonUtils.fromJson(responseBody, RepoIndex::class.java)

    val app = repoIndex.apps.firstOrNull { it.packageName == BuildConfig.APPLICATION_ID }

    val updates = repoIndex.packages.updates ?: return
    val updateDescriptor = if (includeBeta) {
      updates.maxByOrNull { it.versionCode }
    } else {
      updates.firstOrNull { it.versionCode == app?.suggestedVersionCode }
    }

    if (updateDescriptor == null) {
      Log.w(TAG, "Invalid update descriptor!")
      return
    } else {
      Log.i(TAG, "Got descriptor: $updateDescriptor")
    }

    if (updateDescriptor.versionCode > getCurrentAppVersionCode()) {
      val digest: ByteArray = Hex.fromStringCondensed(updateDescriptor.digest)
      val downloadStatus: DownloadStatus = getDownloadStatus(updateDescriptor.url, digest)

      Log.i(TAG, "Download status: ${downloadStatus.status}")

      if (downloadStatus.status == DownloadStatus.Status.COMPLETE) {
        Log.i(TAG, "Download status complete, notifying...")
        handleDownloadComplete(downloadStatus.downloadId)
      } else if (downloadStatus.status == DownloadStatus.Status.MISSING) {
        Log.i(TAG, "Download status missing, starting download...")
        handleDownloadStart(updateDescriptor.url, updateDescriptor.versionName, digest)
      }
    }
  }

  public override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException
  }

  override fun onFailure() {
    Log.w(TAG, "Update check failed")
  }

  @Throws(PackageManager.NameNotFoundException::class)
  private fun getCurrentAppVersionCode(): Int {
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    return packageInfo.versionCode
  }

  private fun getDownloadStatus(uri: Uri, remoteDigest: ByteArray): DownloadStatus {
    val pendingDownloadId: Long = SignalStore.apkUpdate().downloadId
    val pendingDigest: ByteArray? = SignalStore.apkUpdate().digest

    if (pendingDownloadId == -1L || pendingDigest == null || !MessageDigest.isEqual(pendingDigest, remoteDigest)) {
      SignalStore.apkUpdate().clearDownloadAttributes()
      return DownloadStatus(DownloadStatus.Status.MISSING, -1)
    }

    val query = DownloadManager.Query().apply {
      setFilterByStatus(DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_SUCCESSFUL)
      setFilterById(pendingDownloadId)
    }

    context.getDownloadManager().query(query).forEach { cursor ->
      val jobStatus = cursor.requireInt(DownloadManager.COLUMN_STATUS)
      val jobRemoteUri = cursor.requireString(DownloadManager.COLUMN_URI)
      val downloadId = cursor.requireLong(DownloadManager.COLUMN_ID)

      if (jobRemoteUri == uri.toString() && downloadId == pendingDownloadId) {
        return if (jobStatus == DownloadManager.STATUS_SUCCESSFUL) {
          val digest = getDigestForDownloadId(downloadId)
          if (digest != null && MessageDigest.isEqual(digest, remoteDigest)) {
            DownloadStatus(DownloadStatus.Status.COMPLETE, downloadId)
          } else {
            Log.w(TAG, "Found downloadId $downloadId, but the digest doesn't match! Considering it missing.")
            SignalStore.apkUpdate().clearDownloadAttributes()
            DownloadStatus(DownloadStatus.Status.MISSING, downloadId)
          }
        } else {
          DownloadStatus(DownloadStatus.Status.PENDING, downloadId)
        }
      }
    }

    return DownloadStatus(DownloadStatus.Status.MISSING, -1)
  }

  private fun handleDownloadStart(uri: Uri, versionName: String?, digest: ByteArray) {
    deleteExistingDownloadedApks(context)

    val downloadRequest = DownloadManager.Request(uri).apply {
      setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
      setTitle("Downloading ${R.string.app_name} update")
      setDescription("Downloading ${R.string.app_name} $versionName")
      setDestinationInExternalFilesDir(context, null, "molly-update.apk")
      setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
    }

    val downloadId = context.getDownloadManager().enqueue(downloadRequest)
    // DownloadManager will trigger [UpdateApkReadyListener] when finished via a broadcast

    SignalStore.apkUpdate().setDownloadAttributes(downloadId, digest)
  }

  private fun handleDownloadComplete(downloadId: Long) {
    val intent = Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
    ApkUpdateDownloadManagerReceiver().onReceive(context, intent)
  }

  private fun getDigestForDownloadId(downloadId: Long): ByteArray? {
    return try {
      FileInputStream(context.getDownloadManager().openDownloadedFile(downloadId).fileDescriptor).use { stream ->
        FileUtils.getFileDigest(stream)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to get digest for downloadId! $downloadId", e)
      null
    }
  }

  private fun deleteExistingDownloadedApks(context: Context) {
    val directory = context.getExternalFilesDir(null)
    if (directory == null) {
      Log.w(TAG, "Failed to read external files directory.")
      return
    }

    for (file in directory.listFiles() ?: emptyArray()) {
      if (file.name.startsWith("molly-update")) {
        if (file.delete()) {
          Log.d(TAG, "Deleted " + file.name)
        }
      }
    }
  }

  private data class RepoIndex(
    @JsonProperty val apps: List<App>,
    @JsonProperty val packages: Packages,
  ) {
    data class App(
      @JsonProperty val packageName: String,
      @JsonProperty val suggestedVersionCode: Int,
    )

    data class Packages(
      @JsonProperty(BuildConfig.APPLICATION_ID) val updates: List<UpdateDescriptor>?,
    )
  }

  data class UpdateDescriptor(
    @JsonProperty val versionCode: Int,
    @JsonProperty val versionName: String,
    @JsonProperty val apkName: String,
    @JsonProperty("hash") val digest: String,
  ) {
    val url: Uri = Uri.parse(BuildConfig.FDROID_UPDATE_URL).buildUpon().appendPath(apkName).build()
  }

  private class DownloadStatus(val status: Status, val downloadId: Long) {
    enum class Status {
      PENDING, COMPLETE, MISSING
    }
  }

  class Factory : Job.Factory<ApkUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ApkUpdateJob {
      return ApkUpdateJob(parameters)
    }
  }
}
