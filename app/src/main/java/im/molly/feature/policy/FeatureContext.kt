/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.policy

sealed interface FeatureContext {
  val key: String
}

object NoContext : FeatureContext {
  override val key: String = "none"
}

data class RecipientContext(val recipientId: Long) : FeatureContext {
  override val key: String = "r:$recipientId"
}
