package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request body JSON for verifying stored KBS auth credentials.
 */
class BackupAuthCheckRequest(
  @JsonProperty("number")
  val number: String?,

  @JsonProperty("passwords")
  val passwords: List<String>
)
