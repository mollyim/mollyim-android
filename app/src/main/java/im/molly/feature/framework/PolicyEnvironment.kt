/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.framework

import im.molly.feature.policy.Feature
import im.molly.feature.policy.FeatureContext
import kotlinx.coroutines.flow.StateFlow

class PolicyEnvironment(
  val lockdown: Boolean = false
) {
  fun <C : FeatureContext> isFeatureAllowed(
    feature: Feature<C>, context: C
  ): Boolean {
    val result = feature.policy.evaluate(this, context)
    return result.allowed
  }

  fun <C : FeatureContext> observeFeature(
    feature: Feature<C>, context: C
  ): StateFlow<Boolean> {
    TODO()
  }
}
