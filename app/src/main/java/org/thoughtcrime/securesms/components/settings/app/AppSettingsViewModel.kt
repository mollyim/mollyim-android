package org.thoughtcrime.securesms.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.Store

class AppSettingsViewModel : ViewModel() {

  private val store = Store(AppSettingsState(Recipient.self(), 0))

  private val unreadPaymentsLiveData = UnreadPaymentsLiveData()
  private val selfLiveData: LiveData<Recipient> = Recipient.self().live().liveData

  val state: LiveData<AppSettingsState> = store.stateLiveData

  init {
    store.update(unreadPaymentsLiveData) { payments, state -> state.copy(unreadPaymentsCount = payments.transform { it.unreadCount }.or(0)) }
    store.update(selfLiveData) { self, state -> state.copy(self = self) }
  }

  companion object {
    private val TAG = Log.tag(AppSettingsViewModel::class.java)
  }
}
