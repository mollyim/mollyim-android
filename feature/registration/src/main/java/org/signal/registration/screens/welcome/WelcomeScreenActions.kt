/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

sealed interface WelcomeScreenActions {
  /** Open the terms and privacy policy page. */
  data object ViewTermsAndPrivacy : WelcomeScreenActions

  /** MOLLY: Open the app's proxy and network settings. */
  data object ViewProxySettings : WelcomeScreenActions
}
