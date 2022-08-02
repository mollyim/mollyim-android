package org.thoughtcrime.securesms.conversation

/**
 * State holder for different values we are interested in for a given
 * conversation. This is to be used for different values normally stored
 * directly in the fragment that would be better relegated to a ViewModel.
 */
data class ConversationState(
  val securityInfo: ConversationSecurityInfo = ConversationSecurityInfo(),
) {
  companion object {
    @JvmStatic
    fun create(): ConversationState {
      return ConversationState()
    }
  }

  fun withSecurityInfo(securityInfo: ConversationSecurityInfo) = copy(securityInfo = securityInfo)
}
