package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VerificationSessionMetadataRequestBody(
  @JsonProperty("number") val number: String,
  @JsonProperty("pushToken") val pushToken: String?,
  @JsonProperty("mcc") val mcc: String?,
  @JsonProperty("mnc") val mnc: String?
) {
  @JsonProperty("pushTokenType")
  val pushTokenType: String? = if (pushToken != null) "fcm" else null
}
