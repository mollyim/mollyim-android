/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.testutil.MasterSecretRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CoreUiDependenciesProviderTest {

  @get:Rule
  val masterSecret = MasterSecretRule()

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Before
  fun setUp() {
    every { signalStore.settings.isScreenSecurityEnabled } returns false
  }

  @After
  fun tearDown() {
    ScreenLockController.enableAutoLock(false)
  }

  @Test
  @Config(sdk = [32])
  fun `auto-lock forces screen security before API 33`() {
    ScreenLockController.enableAutoLock(true)

    assertThat(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled()).isTrue()

    ScreenLockController.enableAutoLock(false)

    assertThat(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled()).isFalse()
  }

  @Test
  @Config(sdk = [33])
  fun `auto-lock does not force screen security on API 33+`() {
    ScreenLockController.enableAutoLock(true)

    assertThat(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled()).isFalse()
  }

  @Test
  @Config(sdk = [33])
  fun `screen security setting still applies during auto-lock on API 33+`() {
    every { signalStore.settings.isScreenSecurityEnabled } returns true
    ScreenLockController.enableAutoLock(true)

    assertThat(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled()).isTrue()
  }

  @Test
  @Config(sdk = [33])
  fun `locked master secret forces screen security`() {
    KeyCachingService.clearMasterSecret()

    assertThat(CoreUiDependenciesProvider.provideIsScreenSecurityEnabled()).isTrue()
  }
}
