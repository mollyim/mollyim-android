package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ThemeUtil

enum class MessageRequestBarColorTheme(
  @AttrRes private val containerBackgroundColor: Int,
  @AttrRes private val buttonBackgroundColor: Int,
  @AttrRes private val buttonForegroundDenyColor: Int,
  @AttrRes private val buttonForegroundAcceptColor: Int
) {
  WALLPAPER(
    R.attr.message_request_bar_container_background_wallpaper,
    R.attr.message_request_bar_background_wallpaper,
    R.attr.message_request_bar_denyForeground_wallpaper,
    R.attr.message_request_bar_acceptForeground_wallpaper
  ),
  NORMAL(
    R.attr.message_request_bar_container_background_normal,
    R.attr.message_request_bar_background_normal,
    R.attr.message_request_bar_denyForeground_normal,
    R.attr.message_request_bar_acceptForeground_normal
  );

  @ColorInt
  fun getContainerButtonBackgroundColor(context: Context): Int = ThemeUtil.getThemedColor(context, containerBackgroundColor)

  @ColorInt
  fun getButtonBackgroundColor(context: Context): Int = ThemeUtil.getThemedColor(context, buttonBackgroundColor)

  @ColorInt
  fun getButtonForegroundDenyColor(context: Context): Int = ThemeUtil.getThemedColor(context, buttonForegroundDenyColor)

  @ColorInt
  fun getButtonForegroundAcceptColor(context: Context): Int = ThemeUtil.getThemedColor(context, buttonForegroundAcceptColor)

  companion object {
    @JvmStatic
    fun resolveTheme(hasWallpaper: Boolean): MessageRequestBarColorTheme {
      return if (hasWallpaper) WALLPAPER else NORMAL
    }
  }
}
