package org.thoughtcrime.securesms.parental

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.ParentalControlValues
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ParentalControlViewModel(application: Application) : AndroidViewModel(application) {

  data class ThreadItem(val threadId: Long, val displayName: String, val allowed: Boolean)

  private val _parentalEnabled = MutableLiveData(SignalStore.parentalControl.parentalModeEnabled)
  val parentalEnabled: LiveData<Boolean> = _parentalEnabled

  private val _threads = MutableLiveData<List<ThreadItem>>()
  val threads: LiveData<List<ThreadItem>> = _threads

  fun load() {
    viewModelScope.launch(Dispatchers.IO) {
      val allowedIds = SignalStore.parentalControl.getAllowedThreadIds()
      val app = getApplication<Application>()
      val items = mutableListOf<ThreadItem>()
      val cursor = SignalDatabase.threads.getRecentConversationList(
        limit = 500,
        includeInactiveGroups = true,
        hideV1Groups = false
      )
      SignalDatabase.threads.readerFor(cursor).use { reader ->
        var record = reader.getNext()
        while (record != null) {
          items.add(
            ThreadItem(
              threadId = record.threadId,
              displayName = record.recipient.getDisplayName(app),
              allowed = record.threadId in allowedIds
            )
          )
          record = reader.getNext()
        }
      }
      _threads.postValue(items)
    }
  }

  fun setParentalEnabled(enabled: Boolean) {
    SignalStore.parentalControl.parentalModeEnabled = enabled
    _parentalEnabled.value = enabled
  }

  fun toggleThread(threadId: Long, allowed: Boolean) {
    val current = SignalStore.parentalControl.getAllowedThreadIds().toMutableSet()
    if (allowed) current.add(threadId) else current.remove(threadId)
    SignalStore.parentalControl.setAllowedThreadIds(current)
    load()
  }

  fun changePin(context: Context, newPin: String) {
    val salt = SignalStore.parentalControl.getPinSalt()
    SignalStore.parentalControl.parentPinHash = ParentalControlValues.computePinHash(newPin, salt)
    Toast.makeText(context, R.string.parental_pin_changed, Toast.LENGTH_SHORT).show()
  }
}
