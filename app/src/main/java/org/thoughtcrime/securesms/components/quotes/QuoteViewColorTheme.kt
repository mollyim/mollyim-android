package org.thoughtcrime.securesms.components.quotes

import android.content.Context
import androidx.annotation.AttrRes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ThemeUtil

enum class QuoteViewColorTheme(
  @AttrRes private val backgroundColorRes: Int,
  @AttrRes private val barColorRes: Int,
  @AttrRes private val foregroundColorRes: Int
) {

  INCOMING_WALLPAPER(
    R.attr.quote_view_background_incoming_wallpaper,
    R.attr.quote_view_bar_incoming_wallpaper,
    R.attr.quote_view_foreground_incoming_wallpaper
  ),
  INCOMING_NORMAL(
    R.attr.quote_view_background_incoming_normal,
    R.attr.quote_view_bar_incoming_normal,
    R.attr.quote_view_foreground_incoming_normal
  ),
  OUTGOING_WALLPAPER(
    R.attr.quote_view_background_outgoing_wallpaper,
    R.attr.quote_view_bar_outgoing_wallpaper,
    R.attr.quote_view_foreground_outgoing_wallpaper
  ),
  OUTGOING_NORMAL(
    R.attr.quote_view_background_outgoing_normal,
    R.attr.quote_view_bar_outgoing_normal,
    R.attr.quote_view_foreground_outgoing_normal
  );

  fun getBackgroundColor(context: Context) = ThemeUtil.getThemedColor(context, backgroundColorRes)
  fun getBarColor(context: Context) = ThemeUtil.getThemedColor(context, barColorRes)
  fun getForegroundColor(context: Context) = ThemeUtil.getThemedColor(context, foregroundColorRes)

  companion object {
    @JvmStatic
    fun resolveTheme(isOutgoing: Boolean, isPreview: Boolean, hasWallpaper: Boolean): QuoteViewColorTheme {
      return when {
        isPreview && hasWallpaper -> INCOMING_WALLPAPER
        isPreview && !hasWallpaper -> INCOMING_NORMAL
        isOutgoing && hasWallpaper -> OUTGOING_WALLPAPER
        !isOutgoing && hasWallpaper -> INCOMING_WALLPAPER
        isOutgoing && !hasWallpaper -> OUTGOING_NORMAL
        else -> INCOMING_NORMAL
      }
    }
  }
}
