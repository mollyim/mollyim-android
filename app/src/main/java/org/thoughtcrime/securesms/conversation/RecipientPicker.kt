/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.rememberFragmentState
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Fragments
import org.signal.core.util.DimensionUnit
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.conversation.RecipientPicker.DisplayMode.Companion.flag
import org.thoughtcrime.securesms.conversation.RecipientPickerCallbacks.ContextMenu
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewUtil
import java.util.Optional
import java.util.function.Consumer

/**
 * Provides a recipient search and selection UI.
 */
@Composable
fun RecipientPicker(
  searchQuery: String,
  displayModes: Set<RecipientPicker.DisplayMode> = setOf(RecipientPicker.DisplayMode.ALL),
  selectionLimits: SelectionLimits? = ContactSelectionArguments.Defaults.SELECTION_LIMITS,
  isRefreshing: Boolean,
  focusAndShowKeyboard: Boolean = LocalConfiguration.current.screenHeightDp.dp > 600.dp,
  pendingRecipientSelections: Set<RecipientId> = emptySet(),
  shouldResetContactsList: Boolean = false,
  listBottomPadding: Dp? = null,
  clipListToPadding: Boolean = ContactSelectionArguments.Defaults.RECYCLER_CHILD_CLIPPING,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
  ) {
    RecipientSearchField(
      searchQuery = searchQuery,
      onFilterChanged = { filter -> callbacks.listActions.onSearchQueryChanged(query = filter) },
      focusAndShowKeyboard = focusAndShowKeyboard,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    )

    RecipientSearchResultsList(
      displayModes = displayModes,
      selectionLimits = selectionLimits,
      searchQuery = searchQuery,
      isRefreshing = isRefreshing,
      pendingRecipientSelections = pendingRecipientSelections,
      shouldResetContactsList = shouldResetContactsList,
      bottomPadding = listBottomPadding,
      clipListToPadding = clipListToPadding,
      callbacks = callbacks,
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 8.dp)
    )
  }
}

/**
 * A search input field for finding recipients.
 *
 * Intended to be a compose-based replacement for [ContactFilterView].
 */
@Composable
private fun RecipientSearchField(
  searchQuery: String,
  onFilterChanged: (String) -> Unit,
  @StringRes hintText: Int? = null,
  focusAndShowKeyboard: Boolean = false,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val wrappedView = remember {
    ContactFilterView(context, null, 0).apply {
      hintText?.let { setHint(it) }
    }
  }

  LaunchedEffect(searchQuery) {
    wrappedView.setText(searchQuery)
  }

  // TODO [jeff] This causes the keyboard to re-open on rotation, which doesn't match the existing behavior of ContactFilterView. To fix this,
  //  RecipientSearchField needs to be converted to compose so we can use FocusRequestor.
  LaunchedEffect(focusAndShowKeyboard) {
    if (focusAndShowKeyboard) {
      wrappedView.focusAndShowKeyboard()
    } else {
      wrappedView.clearFocus()
      ViewUtil.hideKeyboard(wrappedView.context, wrappedView)
    }
  }

  DisposableEffect(onFilterChanged) {
    wrappedView.setOnFilterChangedListener { filter -> onFilterChanged(filter) }
    onDispose {
      wrappedView.setOnFilterChangedListener(null)
    }
  }

  AndroidView(
    factory = { wrappedView },
    modifier = modifier
  )
}

@Composable
private fun RecipientSearchResultsList(
  displayModes: Set<RecipientPicker.DisplayMode>,
  searchQuery: String,
  isRefreshing: Boolean,
  pendingRecipientSelections: Set<RecipientId>,
  shouldResetContactsList: Boolean,
  selectionLimits: SelectionLimits? = ContactSelectionArguments.Defaults.SELECTION_LIMITS,
  bottomPadding: Dp? = null,
  clipListToPadding: Boolean = ContactSelectionArguments.Defaults.RECYCLER_CHILD_CLIPPING,
  callbacks: RecipientPickerCallbacks,
  modifier: Modifier = Modifier
) {
  val fragmentArgs = ContactSelectionArguments(
    displayMode = displayModes.flag,
    isRefreshable = callbacks.refresh != null,
    enableCreateNewGroup = callbacks.newConversation != null,
    enableFindByUsername = callbacks.findByUsername != null,
    enableFindByPhoneNumber = callbacks.findByPhoneNumber != null,
    selectionLimits = selectionLimits,
    recyclerPadBottom = with(LocalDensity.current) { bottomPadding?.toPx()?.toInt() ?: ContactSelectionArguments.Defaults.RECYCLER_PADDING_BOTTOM },
    recyclerChildClipping = clipListToPadding
  ).toArgumentBundle()

  val fragmentState = rememberFragmentState()
  var currentFragment by remember { mutableStateOf<ContactSelectionListFragment?>(null) }
  val coroutineScope = rememberCoroutineScope()

  Fragments.Fragment<ContactSelectionListFragment>(
    arguments = fragmentArgs,
    fragmentState = fragmentState,
    onUpdate = { fragment ->
      currentFragment = fragment
      fragment.view?.setPadding(0, 0, 0, 0)
      fragment.setUpCallbacks(
        callbacks = callbacks,
        coroutineScope = coroutineScope
      )
    },
    modifier = modifier
  )

  var previousQueryText by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(searchQuery) {
    if (previousQueryText != searchQuery) {
      if (searchQuery.isNotBlank()) {
        currentFragment?.setQueryFilter(searchQuery)
      } else {
        currentFragment?.resetQueryFilter()
      }
      previousQueryText = searchQuery
    }
  }

  var wasRefreshing by rememberSaveable { mutableStateOf(isRefreshing) }
  LaunchedEffect(isRefreshing) {
    currentFragment?.isRefreshing = isRefreshing
    if (wasRefreshing && !isRefreshing) {
      currentFragment?.onDataRefreshed()
    }
    wasRefreshing = isRefreshing
  }

  LaunchedEffect(pendingRecipientSelections) {
    if (pendingRecipientSelections.isNotEmpty()) {
      currentFragment?.let { fragment ->
        pendingRecipientSelections.forEach { recipientId ->
          currentFragment?.addRecipientToSelectionIfAble(recipientId)
        }
        callbacks.listActions.onPendingRecipientSelectionsConsumed()

        callbacks.listActions.onSelectionChanged(
          newSelections = fragment.selectedContacts,
          totalMembersCount = fragment.totalMemberCount
        )
      }
    }
  }

  LaunchedEffect(shouldResetContactsList) {
    if (shouldResetContactsList) {
      currentFragment?.reset()
      callbacks.listActions.onContactsListReset()
    }
  }
}

private fun ContactSelectionListFragment.setUpCallbacks(
  callbacks: RecipientPickerCallbacks,
  coroutineScope: CoroutineScope
) {
  val fragment: ContactSelectionListFragment = this

  if (callbacks.newConversation != null) {
    fragment.setNewConversationCallback(object : ContactSelectionListFragment.NewConversationCallback {
      override fun onInvite() = callbacks.newConversation.onInviteToSignal()
      override fun onNewGroup(forceV1: Boolean) = callbacks.newConversation.onCreateNewGroup()
    })
  } else {
    fragment.setNewConversationCallback(null)
  }

  if (callbacks.findByUsername != null || callbacks.findByPhoneNumber != null) {
    fragment.setFindByCallback(object : ContactSelectionListFragment.FindByCallback {
      override fun onFindByUsername() = callbacks.findByUsername?.onFindByUsername() ?: Unit
      override fun onFindByPhoneNumber() = callbacks.findByPhoneNumber?.onFindByPhoneNumber() ?: Unit
    })
  } else {
    fragment.setFindByCallback(null)
  }

  fragment.setOnContactSelectedListener(object : ContactSelectionListFragment.OnContactSelectedListener {
    override fun onBeforeContactSelected(
      isFromUnknownSearchKey: Boolean,
      recipientId: Optional<RecipientId?>,
      number: String?,
      chatType: Optional<ChatType?>,
      resultConsumer: Consumer<Boolean?>
    ) {
      val recipientId = recipientId.orNull()
      val phone = number?.let(::PhoneNumber)

      coroutineScope.launch {
        val shouldAllowSelection = callbacks.listActions.shouldAllowSelection(recipientId, phone)
        if (shouldAllowSelection) {
          callbacks.listActions.onRecipientSelected(recipientId, phone)
        }
        resultConsumer.accept(shouldAllowSelection)
      }
    }

    override fun onContactDeselected(recipientId: Optional<RecipientId?>, number: String?, chatType: Optional<ChatType?>) = Unit

    override fun onSelectionChanged() {
      callbacks.listActions.onSelectionChanged(
        newSelections = fragment.selectedContacts,
        totalMembersCount = fragment.totalMemberCount
      )
    }
  })

  fragment.setOnItemLongClickListener { anchorView, contactSearchKey, recyclerView ->
    if (callbacks.contextMenu != null) {
      coroutineScope.launch { showItemContextMenu(anchorView, contactSearchKey, recyclerView, callbacks.contextMenu) }
      true
    }
    return@setOnItemLongClickListener false
  }

  fragment.setOnRefreshListener { callbacks.refresh?.onRefresh() }
  fragment.setScrollCallback {
    fragment.view?.let { view -> ViewUtil.hideKeyboard(view.context, view) }
  }
}

private suspend fun showItemContextMenu(
  anchorView: View,
  contactSearchKey: ContactSearchKey,
  recyclerView: RecyclerView,
  callbacks: ContextMenu
) {
  val context = anchorView.context
  val recipient = withContext(Dispatchers.IO) {
    Recipient.resolved(contactSearchKey.requireRecipientSearchKey().recipientId)
  }

  val actions = buildList {
    val messageItem = ActionItem(
      iconRes = R.drawable.ic_chat_message_24,
      title = context.getString(R.string.NewConversationActivity__message),
      tintRes = com.google.android.material.R.attr.colorOnSurface,
      action = { callbacks.onMessage(recipient.id) }
    )
    add(messageItem)

    if (!recipient.isSelf && !recipient.isGroup && recipient.isRegistered) {
      val voiceCallItem = ActionItem(
        iconRes = R.drawable.ic_phone_right_24,
        title = context.getString(R.string.NewConversationActivity__audio_call),
        tintRes = com.google.android.material.R.attr.colorOnSurface,
        action = { callbacks.onVoiceCall(recipient) }
      )
      add(voiceCallItem)
    }

    if (!recipient.isSelf && !recipient.isMmsGroup && recipient.isRegistered) {
      val videoCallItem = ActionItem(
        iconRes = R.drawable.ic_video_call_24,
        title = context.getString(R.string.NewConversationActivity__video_call),
        tintRes = com.google.android.material.R.attr.colorOnSurface,
        action = { callbacks.onVideoCall(recipient) }
      )
      add(videoCallItem)
    }

    if (!recipient.isSelf && !recipient.isGroup) {
      val removeItem = ActionItem(
        iconRes = R.drawable.ic_minus_circle_20,
        title = context.getString(R.string.NewConversationActivity__remove),
        tintRes = com.google.android.material.R.attr.colorOnSurface,
        action = { callbacks.onRemove(recipient) }
      )
      add(removeItem)
    }

    if (!recipient.isSelf) {
      val blockItem = ActionItem(
        iconRes = R.drawable.ic_block_tinted_24,
        title = context.getString(R.string.NewConversationActivity__block),
        tintRes = com.google.android.material.R.attr.colorError,
        action = { callbacks.onBlock(recipient) }
      )
      add(blockItem)
    }
  }

  SignalContextMenu.Builder(anchorView, anchorView.getRootView() as ViewGroup)
    .preferredVerticalPosition(SignalContextMenu.VerticalPosition.BELOW)
    .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
    .offsetX(DimensionUnit.DP.toPixels(12f).toInt())
    .offsetY(DimensionUnit.DP.toPixels(12f).toInt())
    .onDismiss { recyclerView.suppressLayout(false) }
    .show(actions)

  recyclerView.suppressLayout(true)
}

@DayNightPreviews
@Composable
private fun RecipientPickerPreview() {
  RecipientPicker(
    searchQuery = "",
    isRefreshing = false,
    shouldResetContactsList = false,
    callbacks = RecipientPickerCallbacks(
      listActions = RecipientPickerCallbacks.ListActions.Empty
    )
  )
}

data class RecipientPickerCallbacks(
  val listActions: ListActions,
  val refresh: Refresh? = null,
  val contextMenu: ContextMenu? = null,
  val newConversation: NewConversation? = null,
  val findByUsername: FindByUsername? = null,
  val findByPhoneNumber: FindByPhoneNumber? = null
) {
  interface ListActions {
    /**
     * Validates whether the selection of [RecipientId] should be allowed. Return true if the selection can proceed, false otherwise.
     *
     * This is called before [onRecipientSelected] to provide a chance to prevent the selection.
     */
    fun onSearchQueryChanged(query: String)
    suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean
    fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?)
    fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) = Unit
    fun onPendingRecipientSelectionsConsumed()
    fun onContactsListReset() = Unit

    object Empty : ListActions {
      override fun onSearchQueryChanged(query: String) = Unit
      override suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean = true
      override fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) = Unit
      override fun onPendingRecipientSelectionsConsumed() = Unit
      override fun onContactsListReset() = Unit
    }
  }

  interface Refresh {
    fun onRefresh()
  }

  interface ContextMenu {
    fun onMessage(id: RecipientId)
    fun onVoiceCall(recipient: Recipient)
    fun onVideoCall(recipient: Recipient)
    fun onRemove(recipient: Recipient)
    fun onBlock(recipient: Recipient)
  }

  interface NewConversation {
    fun onCreateNewGroup()
    fun onInviteToSignal()
  }

  interface FindByUsername {
    fun onFindByUsername()
  }

  interface FindByPhoneNumber {
    fun onFindByPhoneNumber()
  }
}

object RecipientPicker {
  /**
   * Enum wrapper for [ContactSelectionDisplayMode].
   */
  enum class DisplayMode(val flag: Int) {
    PUSH(flag = ContactSelectionDisplayMode.FLAG_PUSH),
    SMS(flag = ContactSelectionDisplayMode.FLAG_SMS),
    ACTIVE_GROUPS(flag = ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS),
    INACTIVE_GROUPS(flag = ContactSelectionDisplayMode.FLAG_INACTIVE_GROUPS),
    SELF(flag = ContactSelectionDisplayMode.FLAG_SELF),
    BLOCK(flag = ContactSelectionDisplayMode.FLAG_BLOCK),
    HIDE_GROUPS_V1(flag = ContactSelectionDisplayMode.FLAG_HIDE_GROUPS_V1),
    HIDE_NEW(flag = ContactSelectionDisplayMode.FLAG_HIDE_NEW),
    HIDE_RECENT_HEADER(flag = ContactSelectionDisplayMode.FLAG_HIDE_RECENT_HEADER),
    GROUPS_AFTER_CONTACTS(flag = ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS),
    GROUP_MEMBERS(flag = ContactSelectionDisplayMode.FLAG_GROUP_MEMBERS),
    ALL(flag = ContactSelectionDisplayMode.FLAG_ALL);

    companion object {
      val Set<DisplayMode>.flag: Int
        get() = fold(initial = 0) { acc, displayMode -> acc or displayMode.flag }
    }
  }
}
