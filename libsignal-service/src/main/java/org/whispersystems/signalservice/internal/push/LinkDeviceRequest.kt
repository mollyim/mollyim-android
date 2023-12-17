package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LinkDeviceRequest(
  @JsonProperty("verificationCode") val verificationCode: String,
  @JsonProperty("accountAttributes") val accountAttributes: AccountAttributes,
  @JsonProperty("aciSignedPreKey") val aciSignedPreKey: SignedPreKeyEntity,
  @JsonProperty("pniSignedPreKey") val pniSignedPreKey: SignedPreKeyEntity,
  @JsonProperty("aciPqLastResortPreKey") val aciPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty("pniPqLastResortPreKey") val pniPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty("gcmToken") val gcmToken: GcmRegistrationId?
)
