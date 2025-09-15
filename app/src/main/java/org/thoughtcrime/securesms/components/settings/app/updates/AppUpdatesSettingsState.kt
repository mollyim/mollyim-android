/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import kotlin.time.Duration

data class AppUpdatesSettingsState(
  val lastCheckedTime: Long,
  val includeBetaEnabled: Boolean,
  val autoUpdateEnabled: Boolean
)
