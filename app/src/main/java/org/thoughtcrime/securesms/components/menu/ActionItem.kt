package org.thoughtcrime.securesms.components.menu

import androidx.annotation.DrawableRes

/**
 * Represents an action to be rendered via [SignalContextMenu] or [SignalBottomActionBar]
 */
data class ActionItem @JvmOverloads constructor(
  @DrawableRes val iconRes: Int,
  val title: CharSequence,
  val tintRes: Int = com.google.android.material.R.attr.colorOnSurface,
  val action: Runnable
)
