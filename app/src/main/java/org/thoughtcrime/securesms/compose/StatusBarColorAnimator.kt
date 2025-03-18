package org.thoughtcrime.securesms.compose

import android.animation.ValueAnimator
import android.app.Activity
import com.google.android.material.R as MaterialR
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.WindowUtil

/**
 * Controls status-bar color based off ability to scroll up
 */
class StatusBarColorAnimator(
  private val activity: Activity
) {
  private var animator: ValueAnimator? = null
  private var previousCanScrollUp: Boolean = false

  private val normalColor = ThemeUtil.getThemedColor(activity, MaterialR.attr.colorSurface)
  private val scrollColor = ThemeUtil.getThemedColor(activity, MaterialR.attr.colorSurfaceContainer)

  fun setCanScrollUp(canScrollUp: Boolean) {
    if (previousCanScrollUp == canScrollUp) {
      return
    }

    previousCanScrollUp = canScrollUp
    applyState(canScrollUp)
  }

  fun setColorImmediate() {
    val end = when {
      previousCanScrollUp -> scrollColor
      else -> normalColor
    }

    animator?.cancel()
    WindowUtil.setStatusBarColor(
      activity.window,
      end
    )
  }

  private fun applyState(canScrollUp: Boolean) {
    val (start, end) = when {
      canScrollUp -> normalColor to scrollColor
      else -> scrollColor to normalColor
    }

    animator?.cancel()
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 200
      addUpdateListener {
        WindowUtil.setStatusBarColor(
          activity.window,
          ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, start, end)
        )
      }
      start()
    }
  }
}
