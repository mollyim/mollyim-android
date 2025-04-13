package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import com.google.android.material.R as MaterialR
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.util.ThemeUtil

class AvatarColorPair private constructor(
  @ColorInt val foregroundColor: Int,
  @ColorInt val backgroundColor: Int
) {
  companion object {
    @JvmStatic
    fun create(context: Context, avatarColor: AvatarColor): AvatarColorPair {
      return when (avatarColor) {
        AvatarColor.UNKNOWN -> AvatarColorPair(
          foregroundColor = ThemeUtil.getThemedColor(context, MaterialR.attr.colorOnSurface),
          backgroundColor = ThemeUtil.getThemedColor(context, MaterialR.attr.colorSurfaceVariant)
        )
        AvatarColor.ON_SURFACE_VARIANT -> AvatarColorPair(
          foregroundColor = ThemeUtil.getThemedColor(context, MaterialR.attr.colorOnSurfaceVariant),
          backgroundColor = ThemeUtil.getThemedColor(context, MaterialR.attr.colorSurfaceVariant)
        )
        else -> AvatarColorPair(
          foregroundColor = Avatars.getForegroundColor(avatarColor).colorInt,
          backgroundColor = avatarColor.colorInt()
        )
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AvatarColorPair

    if (foregroundColor != other.foregroundColor) return false
    if (backgroundColor != other.backgroundColor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = foregroundColor
    result = 31 * result + backgroundColor
    return result
  }
}
