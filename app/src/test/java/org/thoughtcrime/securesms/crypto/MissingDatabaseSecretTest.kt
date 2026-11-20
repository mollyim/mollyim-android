/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MissingDatabaseSecretTest {

  @Test
  fun `existing database never causes replacement database or attachment secrets`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val prefsFile = SignalDatabase.getDatabaseFile(context)
    prefsFile.parentFile?.mkdirs()
    prefsFile.createNewFile()

    PlainTextKeyValueStore.init(context)

    val databaseError = assertThrows(IllegalStateException::class.java) {
      DatabaseSecretProvider.getOrCreateDatabaseSecret(context)
    }
    val attachmentError = assertThrows(IllegalStateException::class.java) {
      AppAttachmentSecretStore.getAttachmentEncryptedSecret(context)
    }

    assertEquals("Database secret is missing for an existing protected database", databaseError.message)
    assertEquals("Attachment secret is missing for an existing protected database", attachmentError.message)

    assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(DATABASE_SECRET_KEY))
    assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(ATTACHMENT_SECRET_KEY))
  }

  companion object {
    private const val DATABASE_SECRET_KEY = "pref_database_encrypted_secret"
    private const val ATTACHMENT_SECRET_KEY = "pref_attachment_encrypted_secret"
  }
}
