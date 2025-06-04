package org.thoughtcrime.securesms.export.model

/**
 * Represents a generic chat message for export.
 */
data class ExportedMessage(
  val id: Long,
  val threadId: Long,
  val senderId: String,         // Identifier for the sender (e.g., recipient ID or E.164 number)
  val senderName: String?,      // Display name of the sender
  val dateSent: Long,           // Timestamp when the message was sent
  val dateReceived: Long,       // Timestamp when the message was received
  val body: String?,            // Text content of the message
  val messageType: String,      // e.g., "incoming", "outgoing", "system"
  val attachments: List<ExportedAttachment> = emptyList(),
  val quote: ExportedQuote? = null, // Information about a quoted message
  val isViewOnce: Boolean = false,
  val isRemoteDelete: Boolean = false
  // Add other relevant fields like reactions, mentions if scope expands
)

data class ExportedQuote(
  val authorId: String,
  val authorName: String?,
  val text: String?,
  val originalTimestamp: Long // Timestamp of the original message being quoted
)
