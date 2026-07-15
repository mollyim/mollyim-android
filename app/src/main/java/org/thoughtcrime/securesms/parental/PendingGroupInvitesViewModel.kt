package org.thoughtcrime.securesms.parental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.ui.GroupChangeErrorCallback
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

data class PendingInviteItem(
  val recipientId: RecipientId,
  val threadId: Long,
  val groupName: String
)

class PendingGroupInvitesViewModel(app: Application) : AndroidViewModel(app) {

  private val repository = MessageRequestRepository(app)

  private val _pendingInvites = MutableLiveData<List<PendingInviteItem>>()
  val pendingInvites: LiveData<List<PendingInviteItem>> = _pendingInvites

  fun load() {
    SignalExecutors.BOUNDED.execute {
      val items = mutableListOf<PendingInviteItem>()
      SignalDatabase.groups.getGroups().use { reader ->
        var groupRecord: GroupRecord? = reader.getNext()
        while (groupRecord != null) {
          val record: GroupRecord = groupRecord
          if (record.isV2Group && record.memberLevel(Recipient.self()) == GroupTable.MemberLevel.PENDING_MEMBER) {
            val threadId = SignalDatabase.threads.getThreadIdFor(record.recipientId)
            if (threadId != null) {
              items += PendingInviteItem(
                recipientId = record.recipientId,
                threadId = threadId,
                groupName = record.title?.ifBlank { null } ?: "Unknown group"
              )
            }
          }
          groupRecord = reader.getNext()
        }
      }
      _pendingInvites.postValue(items)
    }
  }

  fun acceptInvite(item: PendingInviteItem, onError: GroupChangeErrorCallback) {
    repository.acceptMessageRequest(
      item.recipientId,
      item.threadId,
      {
        SignalStore.parentalControl.addAllowedThreadId(item.threadId)
        load()
      },
      onError
    )
  }

  fun declineInvite(item: PendingInviteItem, onError: GroupChangeErrorCallback) {
    repository.deleteMessageRequest(
      item.recipientId,
      item.threadId,
      { load() },
      onError
    )
  }
}
