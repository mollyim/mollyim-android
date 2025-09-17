/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.framework

import im.molly.feature.policy.Feature
import im.molly.feature.policy.FeatureContext
import im.molly.feature.policy.NoContext

class PolicyBuilder<C : FeatureContext> {

  private data class Rule<T : FeatureContext>(
    val name: String,
    val predicate: PolicyEnvironment.(T) -> Boolean
  )

  private val allowRules = mutableListOf<Rule<C>>()
  private val denyRules = mutableListOf<Rule<C>>()

  fun allow(name: String, block: PolicyEnvironment.(C) -> Boolean) {
    allowRules += Rule(name, block)
  }

  fun deny(name: String, block: PolicyEnvironment.(C) -> Boolean) {
    denyRules += Rule(name, block)
  }

  fun denyIfLockdown() = deny("lockdown") { lockdown }

  fun <D : FeatureContext> dependsOn(feature: Feature<D>, contextProvider: (C) -> D) {
    deny("dependency-not-met:${feature::class.simpleName}") {
      !isFeatureAllowed(feature, contextProvider(it))
    }
  }

  fun dependsOn(feature: Feature<NoContext>) = dependsOn(feature, { NoContext })

  internal fun build(defaultAllow: Boolean): Policy<C> = Policy { env, featureCtx ->
    denyRules.firstOrNull { it.predicate(env, featureCtx) }?.let { matchedRule ->
      return@Policy PolicyResult(false, matchedRule.name)
    }

    allowRules.firstOrNull { it.predicate(env, featureCtx) }?.let { matchedRule ->
      return@Policy PolicyResult(true, matchedRule.name)
    }

    // If no specific rules matched, fall back to the feature's default policy.
    if (defaultAllow) {
      PolicyResult(true, "DEFAULT_ALLOW")
    } else {
      PolicyResult(false, "DEFAULT_DENY")
    }
  }
}
