/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request to copy and re-encrypt media from the attachments cdn into the backup cdn.
 */
class ArchiveMediaRequest(
  @JsonProperty("sourceAttachment") val sourceAttachment: SourceAttachment,
  @JsonProperty("objectLength") val objectLength: Int,
  @JsonProperty("mediaId") val mediaId: String,
  @JsonProperty("hmacKey") val hmacKey: String,
  @JsonProperty("encryptionKey") val encryptionKey: String,
  @JsonProperty("iv") val iv: String
) {
  class SourceAttachment(
    @JsonProperty("cdn") val cdn: Int,
    @JsonProperty("key") val key: String
  )
}
