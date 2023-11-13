package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

data class SpamTokenMessage(@JsonProperty("token") val token: String?)
