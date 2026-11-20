/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto

import android.content.Context
import org.signal.core.util.crypto.AttachmentSecretStore
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore

object AppAttachmentSecretStore : AttachmentSecretStore {
  override fun getAttachmentUnencryptedSecret(context: Context): String? {
    return PlainTextKeyValueStore.attachmentLegacyUnencryptedSecret ?: failIfProtectedDatabaseExistsWithoutAnySecret(context)
  }

  override fun getAttachmentEncryptedSecret(context: Context): String? {
    return PlainTextKeyValueStore.attachmentEncryptedSecret ?: failIfProtectedDatabaseExistsWithoutAnySecret(context)
  }

  override fun setAttachmentEncryptedSecret(context: Context, secret: String) {
    PlainTextKeyValueStore.attachmentEncryptedSecret = secret
  }

  override fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
    PlainTextKeyValueStore.attachmentLegacyUnencryptedSecret = secret
  }

  private fun failIfProtectedDatabaseExistsWithoutAnySecret(context: Context): String? {
    val hasAnySecret = PlainTextKeyValueStore.attachmentLegacyUnencryptedSecret != null ||
      PlainTextKeyValueStore.attachmentEncryptedSecret != null
    val hasProtectedDatabase = SignalDatabase.databaseFileExists(context) || KeyValueDatabase.exists(context)

    if (hasAnySecret || !hasProtectedDatabase) {
      return null
    }

    // MOLLY: Existing databases and attachments must never be made unreachable by silently creating another secret.
    throw IllegalStateException("Attachment secret is missing for an existing protected database")
  }
}
