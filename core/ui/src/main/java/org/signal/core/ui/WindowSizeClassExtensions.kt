/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

private const val TABLET_ASPECT_RATIO = 1.5f

val WindowSizeClass.listPaneDefaultPreferredWidth: Dp get() = if (isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) 416.dp else 316.dp
val WindowSizeClass.horizontalPartitionDefaultSpacerSize: Dp get() = 12.dp
val WindowSizeClass.detailPaneMaxContentWidth: Dp get() = 624.dp

val WindowSizeClass.isWidthCompact
  get() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isWidthExpanded
  get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

val WindowSizeClass.isHeightCompact
  get() = !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isHeightExpanded
  get() = isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)

fun Resources.getWindowSizeClass(): WindowSizeClass {
  return WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
    widthDp = displayMetrics.widthPixels / displayMetrics.density,
    heightDp = displayMetrics.heightPixels / displayMetrics.density
  )
}

@Composable
fun rememberWindowBreakpoint(): WindowBreakpoint {
  val resources = LocalResources.current
  val configuration = LocalConfiguration.current

  return remember(resources, configuration) {
    resources.getWindowBreakpoint()
  }
}

/**
 * Determines the device's form factor based on the current [Resources] and window size class.
 *
 * This function uses several heuristics:
 * - Returns [WindowBreakpoint.SMALL] if the width or height is compact
 * - Otherwise, falls back to aspect ratio heuristics: wide windows use [WindowBreakpoint.LARGE_WIDTH], tall windows use [WindowBreakpoint.LARGE_HEIGHT], else [WindowBreakpoint.MEDIUM].
 *
 * @return the inferred [WindowBreakpoint] for the current device.
 */
fun Resources.getWindowBreakpoint(): WindowBreakpoint {
  val windowSizeClass = getWindowSizeClass()

  if (windowSizeClass.isWidthCompact || windowSizeClass.isHeightCompact) {
    return WindowBreakpoint.SMALL
  }

  val numerator = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val denominator = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val aspectRatio = numerator.toFloat() / denominator

  return when {
    aspectRatio < TABLET_ASPECT_RATIO -> WindowBreakpoint.MEDIUM
    else -> {
      if (displayMetrics.widthPixels >= displayMetrics.heightPixels) {
        WindowBreakpoint.LARGE_WIDTH
      } else {
        WindowBreakpoint.LARGE_HEIGHT
      }
    }
  }
}

/**
 * Indicates the general form factor of the device for responsive UI purposes.
 *
 * - [SMALL]: A window with a compact width or height, typical of phone-sized devices.
 * - [MEDIUM]: A window where neither width nor height is compact or expanded, typical of foldables.
 * - [LARGE_WIDTH]: A window with expanded width and medium height, typical of tablets in landscape orientation.
 * - [LARGE_HEIGHT]: A window with medium width and expanded height, typical of tablets in portrait orientation.
 */
enum class WindowBreakpoint(val isLargeWindow: Boolean) {
  SMALL(isLargeWindow = false),
  MEDIUM(isLargeWindow = false),
  LARGE_WIDTH(isLargeWindow = true),
  LARGE_HEIGHT(isLargeWindow = true)
}

@Composable
fun Resources.rememberIsSplitPane(
  forceSplitPane: Boolean = CoreUiDependencies.forceSplitPane
): Boolean {
  return remember(this, forceSplitPane) {
    isSplitPane(forceSplitPane)
  }
}

/**
 * Determines whether the UI should display in split-pane mode based on available screen space.
 */
@JvmOverloads
fun Resources.isSplitPane(
  forceSplitPane: Boolean = CoreUiDependencies.forceSplitPane
): Boolean {
  if (forceSplitPane) {
    return true
  }

  return when (getWindowBreakpoint()) {
    WindowBreakpoint.SMALL, WindowBreakpoint.LARGE_HEIGHT -> false
    WindowBreakpoint.MEDIUM, WindowBreakpoint.LARGE_WIDTH -> true
  }
}
