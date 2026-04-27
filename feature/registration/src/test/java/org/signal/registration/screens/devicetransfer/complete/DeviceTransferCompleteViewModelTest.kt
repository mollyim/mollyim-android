/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.registration.RegistrationFlowEvent

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTransferCompleteViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: DeviceTransferCompleteViewModel
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<DeviceTransferCompleteState>
  private lateinit var stateEmitter: (DeviceTransferCompleteState) -> Unit

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    emittedEvents = mutableListOf()
    parentEventEmitter = { emittedEvents.add(it) }
    emittedStates = mutableListOf()
    stateEmitter = { emittedStates.add(it) }
    viewModel = DeviceTransferCompleteViewModel(parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `ContinueClicked emits RegistrationComplete`() = runTest {
    viewModel.applyEvent(
      DeviceTransferCompleteState(),
      DeviceTransferCompleteScreenEvents.ContinueClicked,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).containsExactly(RegistrationFlowEvent.RegistrationComplete)
  }
}
