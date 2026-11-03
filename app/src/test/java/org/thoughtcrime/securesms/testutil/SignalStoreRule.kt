/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Installs a real [SignalStore] backed by in-memory storage, letting tests arrange and assert on actual
 * stored state rather than stubbing interactions. Use [MockSignalStoreRule] instead when you need
 * interaction-based mocking.
 */
class SignalStoreRule : TestRule {

  private val storeRule = object : ExternalResource() {
    override fun before() {
      val application = ApplicationProvider.getApplicationContext<Application>()
      SignalStore.testInject(SignalStore(application, KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()))))
    }

    override fun after() {
      SignalStore.testInject(null)
    }
  }

  private val masterSecretRule = MasterSecretRule()

  override fun apply(base: Statement, description: Description): Statement {
    return RuleChain
      .outerRule(masterSecretRule)
      .around(storeRule)
      .apply(base, description)
  }
}
