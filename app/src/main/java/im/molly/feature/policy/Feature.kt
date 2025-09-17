/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.policy

import im.molly.feature.framework.Policy
import im.molly.feature.framework.PolicyBuilder

sealed interface Feature<in C : FeatureContext> {
  val policy: Policy<C>
}

fun <C : FeatureContext> Feature<C>.defaultDeny(
  block: PolicyBuilder<C>.() -> Unit = { }
): Policy<C> = PolicyBuilder<C>().apply(block).build(defaultAllow = false)

fun <C : FeatureContext> Feature<C>.defaultAllow(
  block: PolicyBuilder<C>.() -> Unit = { }
): Policy<C> = PolicyBuilder<C>().apply(block).build(defaultAllow = true)
