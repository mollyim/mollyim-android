package org.thoughtcrime.securesms.components

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.window.WindowSizeClass
import org.thoughtcrime.securesms.window.WindowSizeClass.Companion.getWindowSizeClass
import com.google.android.material.R as MaterialR

/**
 * Forces rounded corners on BottomSheet
 */
abstract class FixedRoundedCornerBottomSheetDialogFragment : BottomSheetDialogFragment() {

  /**
   * Sheet corner radius in DP
   */
  protected val cornerRadius: Int by lazy {
    if (WindowSizeClass.isLargeScreenSupportEnabled() && resources.getWindowSizeClass().isSplitPane()) {
      32
    } else {
      18
    }
  }

  protected open val peekHeightPercentage: Float = 0.5f

  @StyleRes
  protected open val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners

  @ColorInt
  protected var backgroundColor: Int = Color.TRANSPARENT

  private lateinit var dialogBackground: MaterialShapeDrawable

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, themeResId)
  }

  override fun onResume() {
    super.onResume()
    dialog?.window?.let { window ->
      WindowUtil.initializeScreenshotSecurity(requireContext(), window)
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

    dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * peekHeightPercentage).toInt()

    val shapeAppearanceModel = ShapeAppearanceModel.builder()
      .setTopLeftCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), cornerRadius).toFloat())
      .setTopRightCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), cornerRadius).toFloat())
      .build()

    dialogBackground = MaterialShapeDrawable(shapeAppearanceModel)

    val bottomSheetStyle = ThemeUtil.getThemedResourceId(ContextThemeWrapper(requireContext(), themeResId), MaterialR.attr.bottomSheetStyle)
    backgroundColor = ThemeUtil.getThemedColor(ContextThemeWrapper(requireContext(), bottomSheetStyle), MaterialR.attr.backgroundTint)
    dialogBackground.fillColor = ColorStateList.valueOf(backgroundColor)

    dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (bottomSheet.background !== dialogBackground) {
          ViewCompat.setBackground(bottomSheet, dialogBackground)
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    })

    return dialog
  }
}
