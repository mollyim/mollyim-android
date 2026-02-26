/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package im.molly.app.base

import org.thoughtcrime.securesms.BuildConfig

object ApkInfo {
  const val versionName = BuildConfig.VERSION_NAME
  const val versionCode = BuildConfig.VERSION_CODE

  const val signalCanonicalVersionName = BuildConfig.SIGNAL_CANONICAL_VERSION_NAME
  const val signalCanonicalVersionCode = BuildConfig.SIGNAL_CANONICAL_VERSION_CODE
}
