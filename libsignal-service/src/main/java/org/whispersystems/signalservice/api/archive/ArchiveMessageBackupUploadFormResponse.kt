/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the response body when we ask for a message backup upload form.
 */
data class ArchiveMessageBackupUploadFormResponse(
  @JsonProperty("cdn")
  val cdn: Int,
  @JsonProperty("key")
  val key: String,
  @JsonProperty("headers")
  val headers: Map<String, String>,
  @JsonProperty("signedUploadLocation")
  val signedUploadLocation: String
)
