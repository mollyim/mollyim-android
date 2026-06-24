/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Controls the navigation stack used by the chats screen.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class ChatsBackStack(savedStateHandle: SavedStateHandle) {

  companion object {
    private const val KEY = "chats_back_stack"

    val saver: Saver<SnapshotStateList<MainNavigationDetailLocation>, ArrayList<MainNavigationDetailLocation>> = Saver(
      save = { ArrayList(it) },
      restore = { mutableStateListOf(*it.toTypedArray()) }
    )
  }

  val entries: SnapshotStateList<MainNavigationDetailLocation> = savedStateHandle.saveable(
    key = KEY,
    saver = saver
  ) {
    mutableStateListOf(MainNavigationDetailLocation.Empty)
  }

  val activeRecipientId: RecipientId?
    get() = entries.asReversed().firstNotNullOfOrNull {
      when (it) {
        is MainNavigationDetailLocation.Conversation -> it.conversationArgs.recipientId
        is MainNavigationDetailLocation.Chats -> it.controllerKey
        else -> null
      }
    }

  val isEmpty: Boolean
    get() = entries.singleOrNull() is MainNavigationDetailLocation.Empty

  /**
   * Pushes an entry onto the stack.
   */
  fun push(location: MainNavigationDetailLocation) {
    when (location) {
      is MainNavigationDetailLocation.Empty, entries.lastOrNull() -> Unit

      is MainNavigationDetailLocation.Conversation -> {
        entries.removeAll { it !is MainNavigationDetailLocation.Empty }
        entries.add(location)
      }

      else -> entries.add(location)
    }
  }

  /**
   * Pops the top entry off the stack. Returns true if something was popped, false if the stack is already at its root.
   */
  fun pop(): Boolean {
    if (entries.size <= 1) return false
    entries.removeAt(entries.lastIndex)
    return true
  }

  /**
   * Resets the stack to its base empty state.
   */
  fun reset() {
    entries.removeAll { it !is MainNavigationDetailLocation.Empty }
    if (entries.isEmpty()) {
      entries.add(MainNavigationDetailLocation.Empty)
    }
  }
}
