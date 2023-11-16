/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.account

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class AccountAttributes(
  @JsonProperty("signalingKey") val signalingKey: String?,
  @JsonProperty("registrationId") val registrationId: Int,
  @JsonProperty("voice") val voice: Boolean,
  @JsonProperty("video") val video: Boolean,
  @JsonProperty("fetchesMessages") val fetchesMessages: Boolean,
  @JsonProperty("registrationLock") val registrationLock: String?,
  @JsonProperty("unidentifiedAccessKey") val unidentifiedAccessKey: ByteArray?,
  @JsonProperty("unrestrictedUnidentifiedAccess") val unrestrictedUnidentifiedAccess: Boolean,
  @JsonProperty("discoverableByPhoneNumber") val discoverableByPhoneNumber: Boolean,
  @JsonProperty("capabilities") val capabilities: Capabilities?,
  @JsonProperty("name") val name: String?,
  @JsonProperty("pniRegistrationId") val pniRegistrationId: Int,
  @JsonProperty("recoveryPassword") val recoveryPassword: String?
) {
  constructor(
    signalingKey: String?,
    registrationId: Int,
    fetchesMessages: Boolean,
    registrationLock: String?,
    unidentifiedAccessKey: ByteArray?,
    unrestrictedUnidentifiedAccess: Boolean,
    capabilities: Capabilities?,
    discoverableByPhoneNumber: Boolean,
    name: String?,
    pniRegistrationId: Int,
    recoveryPassword: String?
  ) : this(
    signalingKey = signalingKey,
    registrationId = registrationId,
    voice = true,
    video = true,
    fetchesMessages = fetchesMessages,
    registrationLock = registrationLock,
    unidentifiedAccessKey = unidentifiedAccessKey,
    unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess,
    discoverableByPhoneNumber = discoverableByPhoneNumber,
    capabilities = capabilities,
    name = name,
    pniRegistrationId = pniRegistrationId,
    recoveryPassword = recoveryPassword
  )

  data class Capabilities(
    @JsonProperty("storage") val storage: Boolean,
    @JsonProperty("senderKey") val senderKey: Boolean,
    @JsonProperty("announcementGroup") val announcementGroup: Boolean,
    @JsonProperty("changeNumber") val changeNumber: Boolean,
    @JsonProperty("stories") val stories: Boolean,
    @JsonProperty("giftBadges") val giftBadges: Boolean,
    @JsonProperty("pni") val pni: Boolean,
    @JsonProperty("paymentActivation") val paymentActivation: Boolean
  )
}
