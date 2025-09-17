/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.framework

import im.molly.feature.policy.FeatureContext

data class PolicyResult(val allowed: Boolean, val matchedRule: String)

fun interface Policy<in C : FeatureContext> {
  fun evaluate(env: PolicyEnvironment, featureCtx: C): PolicyResult
}
