/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import org.whispersystems.signalservice.api.account.PreKeyCollection
import org.whispersystems.signalservice.api.kbs.MasterKey

data class AccountRegistrationResult(
  val uuid: String,
  val pni: String,
  val storageCapable: Boolean,
  val number: String,
  val masterKey: MasterKey?,
  val pin: String?,
  val aciPreKeyCollection: PreKeyCollection,
  val pniPreKeyCollection: PreKeyCollection,
  val peerExtraPublicKey: ByteArray? = null
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AccountRegistrationResult

    if (uuid != other.uuid) return false
    if (pni != other.pni) return false
    if (storageCapable != other.storageCapable) return false
    if (number != other.number) return false
    if (masterKey != other.masterKey) return false
    if (pin != other.pin) return false
    if (aciPreKeyCollection != other.aciPreKeyCollection) return false
    if (pniPreKeyCollection != other.pniPreKeyCollection) return false
    if (peerExtraPublicKey != null) {
      if (other.peerExtraPublicKey == null) return false
      if (!peerExtraPublicKey.contentEquals(other.peerExtraPublicKey)) return false
    } else if (other.peerExtraPublicKey != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = uuid.hashCode()
    result = 31 * result + pni.hashCode()
    result = 31 * result + storageCapable.hashCode()
    result = 31 * result + number.hashCode()
    result = 31 * result + (masterKey?.hashCode() ?: 0)
    result = 31 * result + (pin?.hashCode() ?: 0)
    result = 31 * result + aciPreKeyCollection.hashCode()
    result = 31 * result + pniPreKeyCollection.hashCode()
    result = 31 * result + (peerExtraPublicKey?.contentHashCode() ?: 0)
    return result
  }
}
