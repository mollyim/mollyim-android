package org.thoughtcrime.securesms.notifications.v2

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.mockk
import org.junit.Test

class NotificationStateParentalFilterTest {

  private fun conversationWithThread(threadId: Long): NotificationConversation {
    return NotificationConversation(
      recipient = mockk(relaxed = true),
      thread = ConversationId(threadId, null),
      notificationItems = listOf(mockk(relaxed = true))
    )
  }

  @Test
  fun `filterThreads - allowed thread is retained`() {
    val state = NotificationState(listOf(conversationWithThread(1L)), emptyList(), emptyList())
    val filtered = state.filterThreads(setOf(1L))
    assertThat(filtered.conversations.map { it.thread.threadId }).isEqualTo(listOf(1L))
  }

  @Test
  fun `filterThreads - disallowed thread is removed`() {
    val state = NotificationState(listOf(conversationWithThread(2L)), emptyList(), emptyList())
    val filtered = state.filterThreads(setOf(1L))
    assertThat(filtered.conversations).isEmpty()
  }

  @Test
  fun `filterThreads - mixed threads keeps only allowed`() {
    val state = NotificationState(
      listOf(conversationWithThread(1L), conversationWithThread(2L), conversationWithThread(3L)),
      emptyList(),
      emptyList()
    )
    val filtered = state.filterThreads(setOf(1L, 3L))
    assertThat(filtered.conversations.map { it.thread.threadId }).isEqualTo(listOf(1L, 3L))
  }

  @Test
  fun `filterThreads - empty allowed set removes all conversations`() {
    val state = NotificationState(
      listOf(conversationWithThread(1L), conversationWithThread(2L)),
      emptyList(),
      emptyList()
    )
    val filtered = state.filterThreads(emptySet())
    assertThat(filtered.conversations).isEmpty()
  }

  @Test
  fun `filterThreads - muteFilteredMessages and profileFilteredMessages are preserved`() {
    val muteMsg = NotificationState.FilteredMessage(1L, false)
    val profileMsg = NotificationState.FilteredMessage(2L, true)
    val state = NotificationState(listOf(conversationWithThread(99L)), listOf(muteMsg), listOf(profileMsg))
    val filtered = state.filterThreads(emptySet())
    assertThat(filtered.muteFilteredMessages).isEqualTo(listOf(muteMsg))
    assertThat(filtered.profileFilteredMessages).isEqualTo(listOf(profileMsg))
  }
}
