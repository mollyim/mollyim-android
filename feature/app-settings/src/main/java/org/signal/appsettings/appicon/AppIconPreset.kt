/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

import android.content.ComponentName
import android.content.Context
import androidx.annotation.AnyRes

/**
 * The icon and localized label from the launcher component's manifest entry.
 *
 * These are resolved from the installed package rather than duplicated in this feature module. Upstream's separate
 * preview drawables described Signal artwork, not Molly's preserved launcher resources.
 */
data class AppIconMetadata(
  @AnyRes val iconResId: Int,
  val label: String
)

/**
 * The stable launcher component associated with each Molly icon choice.
 *
 * Some identifiers preserve upstream names rather than describing Molly's current artwork or localized label.
 */
enum class AppIconPreset(private val componentName: String) {
  DEFAULT(".RoutingActivity"),
  WHITE(".RoutingActivityAltWhite"),
  COLOR(".RoutingActivityAltColor"),
  DARK(".RoutingActivityAltDark"),
  DARK_VARIANT(".RoutingActivityAltDarkVariant"),
  CHAT(".RoutingActivityAltChat"),
  BUBBLES(".RoutingActivityAltBubbles"),
  YELLOW(".RoutingActivityAltYellow"),
  NEWS(".RoutingActivityAltNews"),
  NOTES(".RoutingActivityAltNotes"),
  WEATHER(".RoutingActivityAltWeather"),
  WAVES(".RoutingActivityAltWaves");

  fun getComponentName(context: Context): ComponentName {
    val applicationContext = context.applicationContext
    return ComponentName(applicationContext, "org.thoughtcrime.securesms" + componentName)
  }
}
