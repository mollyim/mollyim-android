package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents an attachment upload form that can be returned by various service endpoints.
 */
data class AttachmentUploadForm(
  @JvmField
  @JsonProperty("cdn")
  val cdn: Int,

  @JvmField
  @JsonProperty("key")
  val key: String,

  @JvmField
  @JsonProperty("headers")
  val headers: Map<String, String>,

  @JvmField
  @JsonProperty("signedUploadLocation")
  val signedUploadLocation: String
)
