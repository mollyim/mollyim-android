package org.thoughtcrime.securesms.badges

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import org.thoughtcrime.securesms.badges.glide.BadgeSpriteTransformation
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ThemeUtil

class BadgeImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  private var badgeSize: Int = 0

  init {
    isClickable = false
  }

  override fun setOnClickListener(l: OnClickListener?) {
    val wasClickable = isClickable
    super.setOnClickListener(l)
    this.isClickable = wasClickable
  }

  fun setBadgeFromRecipient(recipient: Recipient?) {
    getGlideRequestManager()?.let {
      setBadgeFromRecipient(recipient, it)
    } ?: clearDrawable()
  }

  fun setBadgeFromRecipient(recipient: Recipient?, requestManager: RequestManager) {
    if (recipient == null || recipient.badges.isEmpty()) {
      setBadge(null, requestManager)
    } else if (recipient.isSelf) {
      val badge = recipient.featuredBadge
      if (badge == null || !badge.visible || badge.isExpired()) {
        setBadge(null, requestManager)
      } else {
        setBadge(badge, requestManager)
      }
    } else {
      setBadge(recipient.featuredBadge, requestManager)
    }
  }

  fun setBadge(badge: Badge?) {
    getGlideRequestManager()?.let {
      setBadge(badge, it)
    } ?: clearDrawable()
  }

  fun setBadge(badge: Badge?, requestManager: RequestManager) {
    if (badge != null) {
      requestManager
        .load(badge)
        .downsample(DownsampleStrategy.NONE)
        .transform(BadgeSpriteTransformation(BadgeSpriteTransformation.Size.fromInteger(badgeSize), badge.imageDensity, ThemeUtil.isDarkTheme(context)))
        .into(this)

      isClickable = true
    } else {
      requestManager
        .clear(this)
      clearDrawable()
    }
  }

  private fun clearDrawable() {
    if (drawable != null) {
      setImageDrawable(null)
      isClickable = false
    }
  }

  private fun getGlideRequestManager(): RequestManager? {
    return try {
      Glide.with(this)
    } catch (e: IllegalArgumentException) {
      // View not attached to an activity or activity destroyed
      null
    }
  }
}
