/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the response when fetching the archive backup info.
 */
data class ArchiveGetBackupInfoResponse(
  @JsonProperty("cdn")
  val cdn: Int?,
  @JsonProperty("backupDir")
  val backupDir: String?,
  @JsonProperty("backupName")
  val backupName: String?,
  @JsonProperty("usedSpace")
  val usedSpace: Long?
)
