package org.thoughtcrime.securesms.mediapreview

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.PartUriParser
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.rx.RxStore

class MediaPreviewV2ViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(MediaPreviewV2ViewModel::class)
  }

  private val store = RxStore(MediaPreviewV2State())
  private val disposables = CompositeDisposable()
  private val repository: MediaPreviewRepository = MediaPreviewRepository()

  val state: Flowable<MediaPreviewV2State> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())
  val currentPosition: Int
    get() = store.state.position

  fun setIsInSharedAnimation(isInSharedAnimation: Boolean) {
    store.update { it.copy(isInSharedAnimation = isInSharedAnimation) }
  }

  fun shouldFinishAfterTransition(initialMediaUri: Uri): Boolean {
    return currentPosition in store.state.mediaRecords.indices && store.state.mediaRecords[currentPosition].toMedia()?.uri == initialMediaUri
  }

  fun fetchAttachments(context: Context, startingAttachmentId: AttachmentId, threadId: Long, sorting: MediaTable.Sorting, forceRefresh: Boolean = false) {
    if (store.state.loadState == MediaPreviewV2State.LoadState.INIT || forceRefresh) {
      disposables += store.update(repository.getAttachments(context, startingAttachmentId, threadId, sorting)) { result: MediaPreviewRepository.Result, oldState: MediaPreviewV2State ->
        val albums = result.records.fold(mutableMapOf()) { acc: MutableMap<Long, MutableList<Media>>, mediaRecord: MediaTable.MediaRecord ->
          val attachment = mediaRecord.attachment
          if (attachment != null) {
            val convertedMedia = mediaRecord.toMedia() ?: return@fold acc
            acc.getOrPut(attachment.mmsId) { mutableListOf() }.add(convertedMedia)
          }
          acc
        }
        if (oldState.leftIsRecent) {
          oldState.copy(
            position = result.initialPosition,
            mediaRecords = result.records,
            messageBodies = result.messageBodies,
            albums = albums,
            loadState = MediaPreviewV2State.LoadState.DATA_LOADED
          )
        } else {
          oldState.copy(
            position = result.records.size - result.initialPosition - 1,
            mediaRecords = result.records.reversed(),
            messageBodies = result.messageBodies,
            albums = albums.mapValues { it.value.reversed() },
            loadState = MediaPreviewV2State.LoadState.DATA_LOADED
          )
        }
      }
    }
  }

  fun refetchAttachments(context: Context, startingAttachmentId: AttachmentId, threadId: Long, sorting: MediaTable.Sorting) {
    val state = store.state
    val currentAttachmentId = if (state.position in state.mediaRecords.indices) {
      state.mediaRecords[state.position].attachment?.attachmentId
    } else {
      null
    }

    fetchAttachments(context, currentAttachmentId ?: startingAttachmentId, threadId, sorting, true)
  }

  fun initialize(showThread: Boolean, allMediaInAlbumRail: Boolean, leftIsRecent: Boolean) {
    if (store.state.loadState == MediaPreviewV2State.LoadState.INIT) {
      store.update { oldState ->
        oldState.copy(showThread = showThread, allMediaInAlbumRail = allMediaInAlbumRail, leftIsRecent = leftIsRecent)
      }
    }
  }

  fun setCurrentPage(position: Int) {
    store.update { oldState ->
      oldState.copy(position = position)
    }
  }

  fun setMediaReady() {
    store.update { oldState ->
      oldState.copy(loadState = MediaPreviewV2State.LoadState.MEDIA_READY)
    }
  }

  fun remoteDelete(attachment: DatabaseAttachment): Completable {
    return repository.remoteDelete(attachment).subscribeOn(Schedulers.io())
  }

  fun localDelete(context: Context, attachment: DatabaseAttachment): Completable {
    return repository.localDelete(attachment).subscribeOn(Schedulers.io())
  }

  fun jumpToFragment(context: Context, messageId: Long): Single<Intent> {
    return repository.getMessagePositionIntent(context, messageId)
  }

  fun onIncrementalMacError(uri: Uri) {
    maybePostInvalidMacErrorNotification(AppDependencies.application)

    val attachmentId = try {
      val parser = PartUriParser(uri)
      parser.partId
    } catch (e: Exception) {
      Log.w(TAG, "Got an incremental mac error, but could not parse the attachment data from the URI!", e)
      return
    }

    SignalExecutors.BOUNDED.execute {
      Log.w(TAG, "Got an incremental mac error for attachment $attachmentId, clearing the incremental mac data.")
      val attachment = SignalDatabase.attachments.getAttachment(attachmentId) ?: return@execute
      SignalDatabase.attachments.clearIncrementalMacsForAttachmentAndAnyDuplicates(attachmentId, attachment.remoteKey, attachment.dataHash)
    }
  }

  override fun onCleared() {
    disposables.dispose()
    store.dispose()
  }

  fun onDestroyView() {
    store.update { oldState ->
      oldState.copy(loadState = MediaPreviewV2State.LoadState.DATA_LOADED)
    }
  }

  private fun maybePostInvalidMacErrorNotification(context: Context) {
    if (!RemoteConfig.internalUser) {
      return
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "maybePostInvalidMacErrorNotification: Notification permission is not granted.")
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Bad incrementalMac!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }
}

fun MediaTable.MediaRecord.toMedia(): Media? {
  val attachment = this.attachment
  val uri = attachment?.uri
  if (attachment == null || uri == null) {
    return null
  }

  return Media(
    uri = uri,
    contentType = this.contentType,
    date = this.date,
    width = attachment.width,
    height = attachment.height,
    size = attachment.size,
    duration = 0,
    isBorderless = attachment.borderless,
    isVideoGif = attachment.videoGif,
    bucketId = null,
    caption = attachment.caption,
    transformProperties = null,
    fileName = null
  )
}
