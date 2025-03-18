package org.thoughtcrime.securesms.util

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.appbar.AppBarLayout
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Sets the view's isActivated state when the content of the attached recycler can scroll up.
 * This can be used to appropriately tint toolbar backgrounds. Also can emit the state change
 * for other purposes.
 */
open class Material3OnScrollHelper(
  private val context: Context,
  private val setStatusBarColor: (Int) -> Unit,
  private val getStatusBarColor: () -> Int,
  private val setChatFolderColor: (Int) -> Unit = {},
  private val views: List<View>,
  private val viewStubs: List<Stub<out View>> = emptyList(),
  lifecycleOwner: LifecycleOwner
) {

  constructor(activity: Activity, view: View, lifecycleOwner: LifecycleOwner) : this(activity = activity, views = listOf(view), lifecycleOwner = lifecycleOwner)

  constructor(activity: Activity, views: List<View>, viewStubs: List<Stub<out View>> = emptyList(), lifecycleOwner: LifecycleOwner) : this(
    activity = activity,
    views = views,
    viewStubs = viewStubs,
    lifecycleOwner = lifecycleOwner,
    setChatFolderColor = {}
  )

  constructor(
    activity: Activity,
    views: List<View>,
    viewStubs: List<Stub<out View>> = emptyList(),
    lifecycleOwner: LifecycleOwner,
    setChatFolderColor: (Int) -> Unit = {}
  ) : this(
    context = activity,
    setStatusBarColor = { WindowUtil.setStatusBarColor(activity.window, it) },
    getStatusBarColor = { WindowUtil.getStatusBarColor(activity.window) },
    setChatFolderColor = setChatFolderColor,
    views = views,
    viewStubs = viewStubs,
    lifecycleOwner = lifecycleOwner
  )

  open val activeColorSet: ColorSet = ColorSet.from(context,
    toolbarColorRes = MaterialR.attr.colorSurfaceContainer,
    statusBarColorRes = MaterialR.attr.colorSurfaceContainer,
    chatFolderColorRes = MaterialR.attr.colorSurface
  )
  open val inactiveColorSet: ColorSet = ColorSet.from(context,
    toolbarColorRes = MaterialR.attr.colorSurface,
    statusBarColorRes = MaterialR.attr.colorSurface,
    chatFolderColorRes = MaterialR.attr.colorSurfaceContainer
  )

  protected var previousStatusBarColor: Int = getStatusBarColor()

  private var animator: ValueAnimator? = null
  private var active: Boolean? = null

  init {
    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        animator?.cancel()
        setStatusBarColor(previousStatusBarColor)
      }
    })
  }

  fun attach(nestedScrollView: NestedScrollView) {
    nestedScrollView.setOnScrollChangeListener(
      OnScrollListener().apply {
        onScrollChange(nestedScrollView, 0, 0, 0, 0)
      }
    )
  }

  fun attach(recyclerView: RecyclerView) {
    recyclerView.addOnScrollListener(
      OnScrollListener().apply {
        onScrolled(recyclerView, 0, 0)
      }
    )
  }

  fun attach(appBarLayout: AppBarLayout) {
    appBarLayout.addOnOffsetChangedListener(
      OnScrollListener().apply {
        onOffsetChanged(appBarLayout, 0)
      }
    )
  }

  /**
   * Cancels any currently running scroll animation and sets the color immediately.
   */
  fun setColorImmediate() {
    if (active == null) {
      return
    }

    animator?.cancel()
    val colorSet = if (active == true) activeColorSet else inactiveColorSet
    setToolbarColor(colorSet.toolbarColor)
    setStatusBarColor(colorSet.statusBarColor)
    setChatFolderColor(colorSet.chatFolderColor)
  }

  private fun updateActiveState(isActive: Boolean) {
    if (active == isActive) {
      return
    }

    val hadActiveState = active != null
    active = isActive

    views.forEach { it.isActivated = isActive }
    viewStubs.filter { it.resolved() }.forEach { it.get().isActivated = isActive }

    if (animator?.isRunning == true) {
      animator?.reverse()
    } else {
      val startColorSet = if (isActive) inactiveColorSet else activeColorSet
      val endColorSet = if (isActive) activeColorSet else inactiveColorSet

      if (hadActiveState) {
        val startToolbarColor = startColorSet.toolbarColor
        val endToolbarColor = endColorSet.toolbarColor
        val startStatusBarColor = startColorSet.statusBarColor
        val endStatusBarColor = endColorSet.statusBarColor
        val startChatFolderColor = startColorSet.chatFolderColor
        val endChatFolderColor = endColorSet.chatFolderColor

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 200
          addUpdateListener {
            setToolbarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startToolbarColor, endToolbarColor))
            setStatusBarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startStatusBarColor, endStatusBarColor))
            setChatFolderColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startChatFolderColor, endChatFolderColor))
          }
          start()
        }
      } else {
        setColorImmediate()
      }
    }
  }

  private fun setToolbarColor(@ColorInt color: Int) {
    views.forEach { it.setBackgroundColor(color) }
    viewStubs.filter { it.resolved() }.forEach { it.get().setBackgroundColor(color) }
  }

  private inner class OnScrollListener : RecyclerView.OnScrollListener(), AppBarLayout.OnOffsetChangedListener, NestedScrollView.OnScrollChangeListener {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      updateActiveState(recyclerView.canScrollVertically(-1))
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
      updateActiveState(verticalOffset != 0)
    }

    override fun onScrollChange(v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
      updateActiveState(v.canScrollVertically(-1))
    }
  }

  /**
   * A pair of colors tied to a specific state.
   */
  data class ColorSet(
    @ColorInt val toolbarColor: Int,
    @ColorInt val statusBarColor: Int,
    @ColorInt val chatFolderColor: Int
  ) {
    constructor(@ColorInt color: Int) : this(color, color, color)
    constructor(@ColorInt toolbarColor: Int, @ColorInt statusBarColor: Int) : this(toolbarColor, statusBarColor, toolbarColor)

    companion object {
      fun from(context: Context, colorRes: Int) = ColorSet(
        ThemeUtil.getThemedColor(context, colorRes)
      )

      fun from(context: Context, toolbarColorRes: Int, statusBarColorRes: Int) = ColorSet(
        ThemeUtil.getThemedColor(context, toolbarColorRes),
        ThemeUtil.getThemedColor(context, statusBarColorRes)
      )

      fun from(context: Context, toolbarColorRes: Int, statusBarColorRes: Int, chatFolderColorRes: Int) = ColorSet(
        ThemeUtil.getThemedColor(context, toolbarColorRes),
        ThemeUtil.getThemedColor(context, statusBarColorRes),
        ThemeUtil.getThemedColor(context, chatFolderColorRes)
      )
    }
  }
}
