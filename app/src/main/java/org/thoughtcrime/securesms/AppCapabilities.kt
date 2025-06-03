package org.thoughtcrime.securesms

import org.whispersystems.signalservice.api.account.AccountAttributes

object AppCapabilities {
  /**
   * @param storageCapable Whether or not the user can use storage service. This is another way of
   * asking if the user has set a Signal PIN or not.
   */
  @JvmStatic
  fun getCapabilities(storageCapable: Boolean): AccountAttributes.Capabilities {
    return AccountAttributes.Capabilities(
      storage = storageCapable,
      deleteSync = true,
      versionedExpirationTimer = true,
      storageServiceEncryptionV2 = true,
      attachmentBackfill = true,
      extralock = true // Our client supports this, so we advertise it.
    )
  }

  // Conceptually, if we were using rawBits for peer capabilities stored in RecipientRecord:
  // Assume bit 0 is GV2_CALLS, bit 1 is STORIES, etc.
  // This would need to be coordinated with how RecipientRecord.capabilities.rawBits is populated
  // from the server's AccountAttributes.Capabilities.extralock boolean.
  const val EXTRA_LOCK_CAPABILITY_BIT = 1L shl 5 // Example: Assigning bit 5 for ExtraLock.
                                               // Ensure this bit is unique and managed.
}
