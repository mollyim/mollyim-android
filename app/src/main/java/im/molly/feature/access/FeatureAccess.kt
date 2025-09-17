/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.access

import im.molly.feature.framework.PolicyEnvironment
import im.molly.feature.policy.Feature
import im.molly.feature.policy.FeatureContext
import im.molly.feature.policy.NoContext
import kotlinx.coroutines.flow.StateFlow

object FeatureAccess {

  private lateinit var env: PolicyEnvironment

  fun init(env: PolicyEnvironment) {
    this.env = env
  }

  fun <C : FeatureContext> isAllowed(
    feature: Feature<C>, context: C
  ): Boolean {
    return env.isFeatureAllowed(feature, context)
  }

  fun isAllowed(feature: Feature<NoContext>) = env.isFeatureAllowed(feature, NoContext)

  fun <C : FeatureContext> observe(
    feature: Feature<C>, context: C
  ): StateFlow<Boolean> {
    return env.observeFeature(feature, context)
  }

  fun observe(feature: Feature<NoContext>) = env.observeFeature(feature, NoContext)
}
