package org.thoughtcrime.securesms.database.model

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.recipients.RecipientId

data class IdentityStoreRecord(
  val addressName: String,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityTable.VerifiedStatus,
  val firstUse: Boolean,
  val timestamp: Long,
  val nonblockingApproval: Boolean,
  val peerExtraPublicKey: ByteArray? = null
) {
  fun toIdentityRecord(recipientId: RecipientId): IdentityRecord {
    return IdentityRecord(
      recipientId = recipientId,
      identityKey = identityKey,
      verifiedStatus = verifiedStatus,
      firstUse = firstUse,
      timestamp = timestamp,
      nonblockingApproval = nonblockingApproval,
      peerExtraPublicKey = peerExtraPublicKey
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IdentityStoreRecord

    if (addressName != other.addressName) return false
    if (identityKey != other.identityKey) return false
    if (verifiedStatus != other.verifiedStatus) return false
    if (firstUse != other.firstUse) return false
    if (timestamp != other.timestamp) return false
    if (nonblockingApproval != other.nonblockingApproval) return false
    if (peerExtraPublicKey != null) {
      if (other.peerExtraPublicKey == null) return false
      if (!peerExtraPublicKey.contentEquals(other.peerExtraPublicKey)) return false
    } else if (other.peerExtraPublicKey != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = addressName.hashCode()
    result = 31 * result + identityKey.hashCode()
    result = 31 * result + verifiedStatus.hashCode()
    result = 31 * result + firstUse.hashCode()
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + nonblockingApproval.hashCode()
    result = 31 * result + (peerExtraPublicKey?.contentHashCode() ?: 0)
    return result
  }
}
