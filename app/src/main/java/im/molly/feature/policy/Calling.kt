/*
 * Copyright 2025 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.feature.policy

import im.molly.feature.access.FeatureAccess

object Calling : Feature<NoContext> {
  override val policy = defaultAllow { denyIfLockdown() }
}

fun FeatureAccess.canUseCalling() = isAllowed(Calling)
