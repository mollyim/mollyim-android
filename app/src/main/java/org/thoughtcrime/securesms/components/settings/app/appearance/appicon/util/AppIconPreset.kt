/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

enum class AppIconPreset(private val componentName: String, @DrawableRes val iconPreviewResId: Int, @StringRes val labelResId: Int) {
  DEFAULT(".RoutingActivity", R.mipmap.ic_launcher, R.string.app_name),
  LIGHT(".RoutingActivityAltWhite", R.mipmap.ic_launcher_alt_light, R.string.app_name),
  SIGNAL(".RoutingActivityAltColor", R.mipmap.ic_launcher_alt_signal, R.string.app_name),
  COLORFUL(".RoutingActivityAltDark", R.mipmap.ic_launcher_alt_colorful, R.string.app_name),
  GOLD(".RoutingActivityAltDarkVariant", R.mipmap.ic_launcher_alt_gold, R.string.app_name),
  NEON(".RoutingActivityAltChat", R.mipmap.ic_launcher_alt_neon, R.string.app_name),
  CHRISTMAS(".RoutingActivityAltBubbles", R.mipmap.ic_launcher_alt_xmas, R.string.app_name),
  HEART(".RoutingActivityAltYellow", R.mipmap.ic_launcher_alt_heart, R.string.app_name),
  ZEN(".RoutingActivityAltNews", R.mipmap.ic_launcher_alt_zen, R.string.app_icon_label_zen),
  NOTES(".RoutingActivityAltNotes", R.mipmap.ic_launcher_alt_notes, R.string.app_icon_label_notes),
  MOON(".RoutingActivityAltWeather", R.mipmap.ic_launcher_alt_moon, R.string.app_icon_label_moon),
  MUSIC(".RoutingActivityAltWaves", R.mipmap.ic_launcher_alt_music, R.string.app_icon_label_music);

  fun getComponentName(context: Context): ComponentName {
    val applicationContext = context.applicationContext
    return ComponentName(applicationContext, "org.thoughtcrime.securesms" + componentName)
  }
}
