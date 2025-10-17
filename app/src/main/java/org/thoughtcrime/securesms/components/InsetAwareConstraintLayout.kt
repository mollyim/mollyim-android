package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.VerticalInsets
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.window.WindowSizeClass.Companion.getWindowSizeClass
import kotlin.math.roundToInt

/**
 * A specialized [ConstraintLayout] that sets guidelines based on the window insets provided
 * by the system. For improved backwards-compatibility we must use [ViewCompat] for configuring
 * the inset change callbacks.
 *
 * In portrait mode these are how the guidelines will be configured:
 *
 * - [R.id.status_bar_guideline] is set to the bottom of the status bar
 * - [R.id.navigation_bar_guideline] is set to the top of the navigation bar
 * - [R.id.parent_start_guideline] is set to the start of the parent
 * - [R.id.parent_end_guideline] is set to the end of the parent
 * - [R.id.keyboard_guideline] will be set to the top of the keyboard and will
 *   change as the keyboard is shown or hidden
 *
 * In landscape, the spirit of the guidelines are maintained but their names may not
 * correlated exactly to the inset they are providing.
 *
 * These guidelines will only be updated if present in your layout, you can use
 * `<include layout="@layout/system_ui_guidelines" />` to quickly include them.
 */
@Suppress("LeakingThis")
open class InsetAwareConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  companion object {
    private val TAG = Log.tag(InsetAwareConstraintLayout::class.java)
    private val keyboardType = WindowInsetsCompat.Type.ime()
    private val windowTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
  }

  protected val statusBarGuideline: Guideline? by lazy { findViewById(R.id.status_bar_guideline) }
  private val navigationBarGuideline: Guideline? by lazy { findViewById(R.id.navigation_bar_guideline) }
  private val parentStartGuideline: Guideline? by lazy { findViewById(R.id.parent_start_guideline) }
  private val parentEndGuideline: Guideline? by lazy { findViewById(R.id.parent_end_guideline) }
  private val keyboardGuideline: Guideline? by lazy { findViewById(R.id.keyboard_guideline) }

  private val windowInsetsListeners: MutableSet<WindowInsetsListener> = mutableSetOf()
  private val keyboardStateListeners: MutableSet<KeyboardStateListener> = mutableSetOf()
  private val keyboardAnimator = KeyboardInsetAnimator()
  private var overridingKeyboard: Boolean = false
  private var previousKeyboardHeight: Int = 0
  private var applyRootInsets: Boolean = false

  private var insets: WindowInsetsCompat? = null
  private var windowTypes: Int = InsetAwareConstraintLayout.windowTypes
  private var verticalInsetOverride: VerticalInsets = VerticalInsets.Zero

  private val windowInsetsListener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
    this.insets = insets
    applyInsets(windowInsets = insets.getInsets(windowTypes), keyboardInsets = insets.getInsets(keyboardType))
    insets
  }

  val isKeyboardShowing: Boolean
    get() = previousKeyboardHeight > 0

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    ViewCompat.setOnApplyWindowInsetsListener(insetTarget(), windowInsetsListener)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    ViewCompat.setOnApplyWindowInsetsListener(insetTarget(), null)
  }

  init {
    if (attrs != null) {
      context.withStyledAttributes(attrs, R.styleable.InsetAwareConstraintLayout) {
        applyRootInsets = getBoolean(R.styleable.InsetAwareConstraintLayout_applyRootInsets, false)

        if (getBoolean(R.styleable.InsetAwareConstraintLayout_animateKeyboardChanges, false)) {
          ViewCompat.setWindowInsetsAnimationCallback(insetTarget(), keyboardAnimator)
        }
      }
    }
  }

  private fun insetTarget(): View = if (applyRootInsets) rootView else this

  fun setApplyRootInsets(useRootInsets: Boolean) {
    if (applyRootInsets == useRootInsets) {
      return
    }

    ViewCompat.setOnApplyWindowInsetsListener(insetTarget(), null)
    applyRootInsets = useRootInsets
    ViewCompat.setOnApplyWindowInsetsListener(insetTarget(), windowInsetsListener)
  }

  /**
   * Specifies whether or not window insets should be accounted for when applying
   * insets. This is useful when choosing whether to display the content in this
   * constraint layout as a full-window view or as a framed view.
   */
  fun setUseWindowTypes(useWindowTypes: Boolean) {
    windowTypes = if (useWindowTypes) {
      InsetAwareConstraintLayout.windowTypes
    } else {
      0
    }

    if (insets != null) {
      applyInsets(insets!!.getInsets(windowTypes), insets!!.getInsets(keyboardType))
    }
  }

  fun applyInsets(insets: VerticalInsets) {
    verticalInsetOverride = insets

    if (this.insets != null) {
      applyInsets(this.insets!!.getInsets(windowTypes), this.insets!!.getInsets(keyboardType))
    }
  }

  fun addKeyboardStateListener(listener: KeyboardStateListener) {
    keyboardStateListeners += listener
  }

  fun removeKeyboardStateListener(listener: KeyboardStateListener) {
    keyboardStateListeners.remove(listener)
  }

  fun addWindowInsetsListener(listener: WindowInsetsListener) {
    windowInsetsListeners += listener
  }

  fun removeWindowInsetsListener(listener: WindowInsetsListener) {
    windowInsetsListeners.remove(listener)
  }

  private fun applyInsets(windowInsets: Insets, keyboardInsets: Insets) {
    val isLtr = ViewUtil.isLtr(this)

    val statusBar = if (verticalInsetOverride == VerticalInsets.Zero) windowInsets.top else verticalInsetOverride.statusBar.roundToInt()
    val navigationBar = if (verticalInsetOverride == VerticalInsets.Zero) windowInsets.bottom else verticalInsetOverride.navBar.roundToInt()
    val parentStart = if (isLtr) windowInsets.left else windowInsets.right
    val parentEnd = if (isLtr) windowInsets.right else windowInsets.left

    statusBarGuideline?.setGuidelineBegin(statusBar)
    navigationBarGuideline?.setGuidelineEnd(navigationBar)
    parentStartGuideline?.setGuidelineBegin(parentStart)
    parentEndGuideline?.setGuidelineEnd(parentEnd)

    windowInsetsListeners.forEach {
      it.onApplyWindowInsets(statusBar, navigationBar, parentStart, parentEnd)
    }

    if (keyboardInsets.bottom > 0) {
      setKeyboardHeight(keyboardInsets.bottom)
      if (!overridingKeyboard) {
        if (!keyboardAnimator.animating) {
          keyboardGuideline?.setGuidelineEnd(keyboardInsets.bottom)
        } else {
          keyboardAnimator.endingGuidelineEnd = keyboardInsets.bottom
        }
      }
    } else if (!overridingKeyboard) {
      if (!keyboardAnimator.animating) {
        keyboardGuideline?.setGuidelineEnd(windowInsets.bottom)
      } else {
        keyboardAnimator.endingGuidelineEnd = windowInsets.bottom
      }
    }

    if (previousKeyboardHeight != keyboardInsets.bottom) {
      keyboardStateListeners.forEach {
        if (previousKeyboardHeight <= 0 && keyboardInsets.bottom > 0) {
          it.onKeyboardShown()
        } else if (previousKeyboardHeight > 0 && keyboardInsets.bottom <= 0) {
          it.onKeyboardHidden()
        }
      }
    }

    previousKeyboardHeight = keyboardInsets.bottom
  }

  protected fun overrideKeyboardGuidelineWithPreviousHeight() {
    overridingKeyboard = true
    keyboardGuideline?.setGuidelineEnd(getKeyboardHeight())
  }

  protected fun clearKeyboardGuidelineOverride() {
    overridingKeyboard = false
  }

  protected fun resetKeyboardGuideline() {
    clearKeyboardGuidelineOverride()
    keyboardGuideline?.setGuidelineEnd(navigationBarGuideline.guidelineEnd)
    keyboardAnimator.endingGuidelineEnd = navigationBarGuideline.guidelineEnd
  }

  private fun getKeyboardHeight(): Int {
    val height = if (isLandscape()) {
      SignalStore.misc.keyboardLandscapeHeight
    } else {
      SignalStore.misc.keyboardPortraitHeight
    }

    val minHeight = resources.getDimensionPixelSize(R.dimen.default_custom_keyboard_size)
    return if (height > minHeight) {
      height
    } else {
      Log.w(TAG, "Saved keyboard height ($height) is too low, using default size ($minHeight)")
      minHeight
    }
  }

  private fun setKeyboardHeight(height: Int) {
    if (isLandscape()) {
      SignalStore.misc.keyboardLandscapeHeight = height
    } else {
      SignalStore.misc.keyboardPortraitHeight = height
    }
  }

  private fun isLandscape(): Boolean {
    return resources.getWindowSizeClass().isLandscape()
  }

  private val Guideline?.guidelineEnd: Int
    get() = if (this == null) 0 else (layoutParams as LayoutParams).guideEnd

  interface KeyboardStateListener {
    fun onKeyboardShown()
    fun onKeyboardHidden()
  }

  interface WindowInsetsListener {
    fun onApplyWindowInsets(statusBar: Int, navigationBar: Int, parentStart: Int, parentEnd: Int)
  }

  /**
   * Adjusts the [keyboardGuideline] to move with the IME keyboard opening or closing.
   */
  private inner class KeyboardInsetAnimator : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

    var animating = false
      private set

    private var startingGuidelineEnd: Int = 0
    var endingGuidelineEnd: Int = 0
      set(value) {
        field = value
        growing = value > startingGuidelineEnd
      }
    private var growing: Boolean = false

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
      if (overridingKeyboard) {
        return
      }

      animating = true
      startingGuidelineEnd = keyboardGuideline.guidelineEnd
    }

    override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
      if (overridingKeyboard) {
        return insets
      }

      val imeAnimation = runningAnimations.find { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }
      if (imeAnimation == null) {
        return insets
      }

      val estimatedKeyboardHeight: Int = if (growing) {
        endingGuidelineEnd * imeAnimation.interpolatedFraction
      } else {
        startingGuidelineEnd * (1f - imeAnimation.interpolatedFraction)
      }.toInt()

      if (growing) {
        keyboardGuideline?.setGuidelineEnd(estimatedKeyboardHeight.coerceAtLeast(startingGuidelineEnd))
      } else {
        keyboardGuideline?.setGuidelineEnd(estimatedKeyboardHeight.coerceAtLeast(endingGuidelineEnd))
      }

      return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
      if (overridingKeyboard) {
        return
      }

      keyboardGuideline?.setGuidelineEnd(endingGuidelineEnd)
      animating = false
    }
  }
}
