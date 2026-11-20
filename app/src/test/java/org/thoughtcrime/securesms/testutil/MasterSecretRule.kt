/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.service.KeyCachingService
import kotlin.random.Random

/**
 * Ensures the master secret is generated and cached in [KeyCachingService] for tests that exercise
 * code paths that read or write Molly's encrypted shared preferences.
 */
class MasterSecretRule : ExternalResource() {

  override fun before() {
    if (KeyCachingService.isLocked()) {
      val random = Random.nextBytes(32)
      KeyCachingService.setMasterSecret(
        MasterSecret(random, random)
      )
    }
  }

  override fun after() {
    KeyCachingService.clearMasterSecret()
  }
}
