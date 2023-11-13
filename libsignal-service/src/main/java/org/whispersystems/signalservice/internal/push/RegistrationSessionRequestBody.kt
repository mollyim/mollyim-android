package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationSessionRequestBody(
  @JsonProperty("sessionId") val sessionId: String? = null,
  @JsonProperty("recoveryPassword") val recoveryPassword: String? = null,
  @JsonProperty("accountAttributes") val accountAttributes: AccountAttributes,
  @JsonProperty("aciIdentityKey") val aciIdentityKey: String,
  @JsonProperty("pniIdentityKey") val pniIdentityKey: String,
  @JsonProperty("aciSignedPreKey") val aciSignedPreKey: SignedPreKeyEntity,
  @JsonProperty("pniSignedPreKey") val pniSignedPreKey: SignedPreKeyEntity,
  @JsonProperty("aciPqLastResortPreKey") val aciPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty("pniPqLastResortPreKey") val pniPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty("gcmToken") val gcmToken: GcmRegistrationId?,
  @JsonProperty("skipDeviceTransfer") val skipDeviceTransfer: Boolean,
  @JsonProperty("requireAtomic") val requireAtomic: Boolean = true
)
