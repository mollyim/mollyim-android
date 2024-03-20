/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Multi-response data for a batch archive media operation.
 */
class BatchArchiveMediaResponse(
  @JsonProperty("responses") val responses: List<BatchArchiveMediaItemResponse>
) {
  class BatchArchiveMediaItemResponse(
    @JsonProperty("status") val status: Int?,
    @JsonProperty("failureReason") val failureReason: String?,
    @JsonProperty("cdn") val cdn: Int?,
    @JsonProperty("mediaId") val mediaId: String
  )
}
