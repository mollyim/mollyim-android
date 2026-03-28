package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import androidx.annotation.ColorRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.R as MaterialR
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper

/**
 * Scroll helper to manage the color state of the top bar and status bar.
 */
class ConversationToolbarOnScrollHelper(
  activity: FragmentActivity,
  toolbarBackground: View,
  private val wallpaperProvider: () -> ChatWallpaper?,
  lifecycleOwner: LifecycleOwner,
  private val incognito: Boolean = false
) : Material3OnScrollHelper(
  activity = activity,
  views = listOf(toolbarBackground),
  lifecycleOwner = lifecycleOwner,
  setStatusBarColor = {}
) {
  override val activeColorSet: ColorSet
    = if (incognito) ColorSet.from(activity, R.color.conversation_toolbar_color_incognito) else ColorSet.from(activity, getActiveToolbarColor(wallpaperProvider() != null))

  override val inactiveColorSet: ColorSet
    = if (incognito) ColorSet.from(activity, R.color.conversation_toolbar_color_incognito) else ColorSet.from(activity, getInactiveToolbarColor(wallpaperProvider() != null))

  @ColorRes
  private fun getActiveToolbarColor(hasWallpaper: Boolean): Int {
    return if (hasWallpaper) R.color.conversation_toolbar_color_wallpaper_scrolled else MaterialR.attr.colorSurfaceContainer
  }

  @ColorRes
  private fun getInactiveToolbarColor(hasWallpaper: Boolean): Int {
    return if (hasWallpaper) R.color.conversation_toolbar_color_wallpaper else MaterialR.attr.colorSurface
  }
}
