package org.thoughtcrime.securesms.export.model

/**
 * Represents a generic attachment for chat export.
 */
data class ExportedAttachment(
  val contentType: String, // MIME type
  val fileName: String?,    // Original file name, if available
  val localUri: String?,    // URI to the local file, if available (e.g., content URI or file URI)
  val downloadUrl: String?, // Remote URL if it's a cloud attachment not yet downloaded (less common for local export)
  val size: Long,           // Size of the attachment in bytes
  val isVoiceNote: Boolean = false,
  val isSticker: Boolean = false
  // Potentially add width/height for images/videos if needed later
)
