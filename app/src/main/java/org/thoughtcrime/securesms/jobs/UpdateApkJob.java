package org.thoughtcrime.securesms.jobs;


import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.net.Network;
import org.thoughtcrime.securesms.service.UpdateApkReadyListener;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateApkJob extends BaseJob {

  public static final String KEY = "UpdateApkJob";

  private static final String TAG = UpdateApkJob.class.getSimpleName();

  public UpdateApkJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("UpdateApkJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(2)
                           .build());
  }

  private UpdateApkJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, PackageManager.NameNotFoundException {
    if (!TextSecurePreferences.isUpdateApkEnabled(context)) {
      return;
    }

    Log.i(TAG, "Checking for APK update...");

    OkHttpClient client = new OkHttpClient.Builder()
                                          .socketFactory(Network.getSocketFactory())
                                          .dns(Network.getDns())
                                          .build();
    Request request = new Request.Builder().url(String.format("%s/index-v1.json", BuildConfig.FDROID_UPDATE_URL)).build();

    Response response = client.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Bad response: " + response.message());
    }

    RepoIndex repoIndex = JsonUtils.fromJson(response.body().string(), RepoIndex.class);
    if (repoIndex.packages == null ||
        repoIndex.packages.appReleases == null ||
        repoIndex.packages.appReleases.isEmpty()) {
      return;
    }
    Collections.sort(repoIndex.packages.appReleases);

    UpdateDescriptor updateDescriptor = repoIndex.packages.appReleases.get(0);
    byte[]           digest           = Hex.fromStringCondensed(updateDescriptor.getDigest());

    Log.i(TAG, "Got descriptor: " + updateDescriptor);

    if (updateDescriptor.getVersionCode() > getVersionCode()) {
      Uri uri = Uri.parse(BuildConfig.FDROID_UPDATE_URL).buildUpon()
                                                        .appendPath(updateDescriptor.getApkName())
                                                        .build();
      DownloadStatus downloadStatus = getDownloadStatus(uri, digest);

      Log.i(TAG, "Download status: "  + downloadStatus.getStatus());

      if (downloadStatus.getStatus() == DownloadStatus.Status.COMPLETE) {
        Log.i(TAG, "Download status complete, notifying...");
        handleDownloadNotify(downloadStatus.getDownloadId());
      } else if (downloadStatus.getStatus() == DownloadStatus.Status.MISSING) {
        Log.i(TAG, "Download status missing, starting download...");
        handleDownloadStart(uri, updateDescriptor.getVersionName(), digest);
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof  IOException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Update check failed");
  }

  private int getVersionCode() throws PackageManager.NameNotFoundException {
    PackageManager packageManager = context.getPackageManager();
    PackageInfo    packageInfo    = packageManager.getPackageInfo(context.getPackageName(), 0);

    return packageInfo.versionCode;
  }

  private DownloadStatus getDownloadStatus(Uri uri, byte[] theirDigest) {
    DownloadManager       downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    DownloadManager.Query query           = new DownloadManager.Query();

    query.setFilterByStatus(DownloadManager.STATUS_PAUSED | DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_SUCCESSFUL);

    long   pendingDownloadId = TextSecurePreferences.getUpdateApkDownloadId(context);
    byte[] pendingDigest     = getPendingDigest(context);
    Cursor cursor            = downloadManager.query(query);

    try {
      DownloadStatus status = new DownloadStatus(DownloadStatus.Status.MISSING, -1);

      while (cursor != null && cursor.moveToNext()) {
        int    jobStatus         = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        String jobRemoteUri      = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
        long   downloadId        = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
        byte[] digest            = getDigestForDownloadId(downloadId);

        if (jobRemoteUri != null && jobRemoteUri.equals(uri.toString()) && downloadId == pendingDownloadId) {

          if (jobStatus == DownloadManager.STATUS_SUCCESSFUL    &&
              digest != null && pendingDigest != null           &&
              MessageDigest.isEqual(pendingDigest, theirDigest) &&
              MessageDigest.isEqual(digest, theirDigest))
          {
            return new DownloadStatus(DownloadStatus.Status.COMPLETE, downloadId);
          } else if (jobStatus != DownloadManager.STATUS_SUCCESSFUL) {
            status = new DownloadStatus(DownloadStatus.Status.PENDING, downloadId);
          }
        }
      }

      return status;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private void handleDownloadStart(Uri uri, String versionName, byte[] digest) {
    clearPreviousDownloads(context);

    DownloadManager         downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    DownloadManager.Request downloadRequest = new DownloadManager.Request(uri);

    downloadRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
    downloadRequest.setTitle("Downloading Molly update");
    downloadRequest.setDescription("Downloading Molly " + versionName);
    downloadRequest.setVisibleInDownloadsUi(false);
    downloadRequest.setDestinationInExternalFilesDir(context, null, "molly-update.apk");
    downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

    long downloadId = downloadManager.enqueue(downloadRequest);
    TextSecurePreferences.setUpdateApkDownloadId(context, downloadId);
    TextSecurePreferences.setUpdateApkDigest(context, Hex.toStringCondensed(digest));
  }

  private void handleDownloadNotify(long downloadId) {
    Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);

    new UpdateApkReadyListener().onReceive(context, intent);
  }

  private @Nullable byte[] getDigestForDownloadId(long downloadId) {
    try {
      DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
      FileInputStream fin             = new FileInputStream(downloadManager.openDownloadedFile(downloadId).getFileDescriptor());
      byte[]          digest          = FileUtils.getFileDigest(fin);

      fin.close();

      return digest;
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private @Nullable byte[] getPendingDigest(Context context) {
    try {
      String encodedDigest = TextSecurePreferences.getUpdateApkDigest(context);

      if (encodedDigest == null) return null;

      return Hex.fromStringCondensed(encodedDigest);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private static void clearPreviousDownloads(@NonNull Context context) {
    File directory = context.getExternalFilesDir(null);

    if (directory == null) {
      Log.w(TAG, "Failed to read external files directory.");
      return;
    }

    for (File file : directory.listFiles()) {
      if (file.getName().startsWith("molly-update")) {
        if (file.delete()) {
          Log.d(TAG, "Deleted " + file.getName());
        }
      }
    }
  }

  private static class RepoIndex {
    @JsonProperty
    PackageIndex packages;
  }

  private static class PackageIndex {
    @JsonProperty(BuildConfig.APPLICATION_ID)
    List<UpdateDescriptor> appReleases;
  }

  private static class UpdateDescriptor implements Comparable<UpdateDescriptor> {
    @JsonProperty
    private int versionCode;

    @JsonProperty
    private String versionName;

    @JsonProperty
    private String apkName;

    @JsonProperty
    private String hash;

    @Override
    public int compareTo(UpdateDescriptor o) {
      return Integer.compare(o.versionCode, versionCode);
    }

    public int getVersionCode() {
      return versionCode;
    }

    public String getVersionName() {
      return versionName;
    }

    public String getApkName() {
      return apkName;
    }

    public @NonNull String toString() {
      return "["  + versionCode + ", " + versionName + ", " + apkName + "]";
    }

    public String getDigest() {
      return hash;
    }
  }

  private static class DownloadStatus {
    enum Status {
      PENDING,
      COMPLETE,
      MISSING
    }

    private final Status status;
    private final long   downloadId;

    DownloadStatus(Status status, long downloadId) {
      this.status     = status;
      this.downloadId = downloadId;
    }

    public Status getStatus() {
      return status;
    }

    public long getDownloadId() {
      return downloadId;
    }
  }

  public static final class Factory implements Job.Factory<UpdateApkJob> {
    @Override
    public @NonNull UpdateApkJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new UpdateApkJob(parameters);
    }
  }
}
