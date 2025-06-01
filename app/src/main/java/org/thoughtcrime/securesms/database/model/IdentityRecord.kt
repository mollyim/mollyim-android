package org.thoughtcrime.securesms.database.model

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.recipients.RecipientId

data class IdentityRecord(
  val recipientId: RecipientId,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityTable.VerifiedStatus,
  @get:JvmName("isFirstUse")
  val firstUse: Boolean,
  val timestamp: Long,
  @get:JvmName("isApprovedNonBlocking")
  val nonblockingApproval: Boolean,
  val peerExtraPublicKey: ByteArray? = null
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IdentityRecord

    if (recipientId != other.recipientId) return false
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
    var result = recipientId.hashCode()
    result = 31 * result + identityKey.hashCode()
    result = 31 * result + verifiedStatus.hashCode()
    result = 31 * result + firstUse.hashCode()
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + nonblockingApproval.hashCode()
    result = 31 * result + (peerExtraPublicKey?.contentHashCode() ?: 0)
    return result
  }
}
