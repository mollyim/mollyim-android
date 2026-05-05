package org.thoughtcrime.securesms.conversationlist

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.thoughtcrime.securesms.conversationlist.model.Conversation
import org.thoughtcrime.securesms.database.model.ThreadRecord

class ConversationListParentalFilterTest {

  private fun threadConversation(id: Long): Conversation {
    val thread = mockk<ThreadRecord>(relaxed = true)
    every { thread.threadId } returns id
    return Conversation(thread)
  }

  private fun headerConversation(type: Conversation.Type): Conversation {
    val thread = mockk<ThreadRecord>(relaxed = true)
    every { thread.threadId } returns -(type.ordinal.toLong() + 1)
    every { thread.body } returns type.name
    return Conversation(thread)
  }

  @Test
  fun `parental mode OFF - all threads returned unchanged`() {
    val t1 = threadConversation(1L)
    val t2 = threadConversation(2L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(t1, t2), enabled = false, allowedIds = setOf(1L))
    assertThat(result).containsExactly(t1, t2)
  }

  @Test
  fun `parental mode ON - allowed thread passes through`() {
    val t1 = threadConversation(1L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(t1), enabled = true, allowedIds = setOf(1L))
    assertThat(result).containsExactly(t1)
  }

  @Test
  fun `parental mode ON - disallowed thread is removed`() {
    val t1 = threadConversation(1L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(t1), enabled = true, allowedIds = setOf(99L))
    assertThat(result).isEmpty()
  }

  @Test
  fun `parental mode ON - only allowed thread remains when list has both`() {
    val allowed = threadConversation(1L)
    val blocked = threadConversation(2L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(allowed, blocked), enabled = true, allowedIds = setOf(1L))
    assertThat(result).containsExactly(allowed)
  }

  @Test
  fun `parental mode ON - empty allowed set removes all threads`() {
    val t1 = threadConversation(1L)
    val t2 = threadConversation(2L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(t1, t2), enabled = true, allowedIds = emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `parental mode ON - non-THREAD header items are never filtered`() {
    val header = headerConversation(Conversation.Type.PINNED_HEADER)
    val allowed = threadConversation(1L)
    val blocked = threadConversation(2L)
    val footer = headerConversation(Conversation.Type.ARCHIVED_FOOTER)
    val result = ConversationListViewModel.applyParentalFilter(
      listOf(header, allowed, blocked, footer),
      enabled = true,
      allowedIds = setOf(1L)
    )
    assertThat(result).containsExactly(header, allowed, footer)
  }

  @Test
  fun `parental mode ON - all threads allowed returns full list`() {
    val t1 = threadConversation(1L)
    val t2 = threadConversation(2L)
    val result = ConversationListViewModel.applyParentalFilter(listOf(t1, t2), enabled = true, allowedIds = setOf(1L, 2L))
    assertThat(result).containsExactly(t1, t2)
  }

  @Test
  fun `empty list - always returns empty`() {
    val result = ConversationListViewModel.applyParentalFilter(emptyList(), enabled = true, allowedIds = setOf(1L))
    assertThat(result).isEmpty()
  }
}
