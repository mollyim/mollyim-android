/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent

@OptIn(ExperimentalCoroutinesApi::class)
class EnterAepForLocalBackupViewModelTest {

  private lateinit var viewModel: EnterAepForLocalBackupViewModel
  private lateinit var resultBus: ResultEventBus
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  private val resultKey = "test-result-key"

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    resultBus = ResultEventBus()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    viewModel = EnterAepForLocalBackupViewModel(
      parentEventEmitter = parentEventEmitter,
      resultBus = resultBus,
      resultKey = resultKey
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== BackupKeyChanged Tests ====================

  @Test
  fun `BackupKeyChanged updates backup key in state`() = runTest {
    val testKey = VALID_AEP

    viewModel.onEvent(EnterAepEvents.BackupKeyChanged(testKey))
    advanceUntilIdle()

    assertThat(viewModel.state.value.backupKey).isEqualTo(testKey)
  }

  // ==================== Submit Tests ====================

  @Test
  fun `Submit with valid key sends result via resultBus and emits NavigateBack`() = runTest {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged(VALID_AEP))
    viewModel.onEvent(EnterAepEvents.Submit)
    advanceUntilIdle()

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isEqualTo(VALID_AEP)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `Submit with invalid key does not send result or navigate`() = runTest {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged("too-short"))
    viewModel.onEvent(EnterAepEvents.Submit)
    advanceUntilIdle()

    assertThat(resultBus.channelMap[resultKey]).isNull()
    assertThat(emittedParentEvents).isEmpty()
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest {
    viewModel.onEvent(EnterAepEvents.Cancel)
    advanceUntilIdle()

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError clears registrationError from state`() = runTest {
    viewModel.onEvent(EnterAepEvents.DismissError)
    advanceUntilIdle()

    assertThat(viewModel.state.value.registrationError).isNull()
  }

  // ==================== Constants ====================

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }
}
