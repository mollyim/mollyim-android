/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SignalPasskeyManagerTest {

  @Test
  @Config(sdk = [34])
  fun `isSupported is true on API 34`() {
    assertThat(SignalPasskeyManager.isSupported(context())).isTrue()
  }

  @Test
  @Config(sdk = [33])
  fun `isSupported is false below API 34`() {
    assertThat(SignalPasskeyManager.isSupported(context())).isFalse()
  }

  private fun context(): Context = ApplicationProvider.getApplicationContext()
}
