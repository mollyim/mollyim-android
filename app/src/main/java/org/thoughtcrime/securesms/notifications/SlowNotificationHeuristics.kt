/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.text.TextUtils
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.LocaleFeatureFlags
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class Configuration(
  val minimumEventAgeMs: Long,
  val minimumServiceEventCount: Int,
  val serviceStartFailurePercentage: Float,
  val weeklyFailedQueueDrains: Int,
  val minimumMessageLatencyEvents: Int,
  val messageLatencyThreshold: Long,
  val messageLatencyPercentage: Int
)
