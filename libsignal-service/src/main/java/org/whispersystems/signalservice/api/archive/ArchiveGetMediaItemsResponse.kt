/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response body for getting the media items stored in the user's archive.
 */
class ArchiveGetMediaItemsResponse(
  @JsonProperty("storedMediaObjects") val storedMediaObjects: List<StoredMediaObject>,
  @JsonProperty("cursor") val cursor: String?
) {
  class StoredMediaObject(
    @JsonProperty("cdn") val cdn: Int,
    @JsonProperty("mediaId") val mediaId: String,
    @JsonProperty("objectLength") val objectLength: Long
  )
}
