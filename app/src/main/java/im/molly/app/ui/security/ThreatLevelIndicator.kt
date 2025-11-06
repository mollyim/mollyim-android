package im.molly.app.ui.security

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import im.molly.security.ThreatCategory
import org.thoughtcrime.securesms.R

/**
 * MIL-SPEC Threat Level Indicator Widget
 *
 * Visual representation of current threat level with:
 * - Color-coded arc (green -> amber -> orange -> red -> magenta)
 * - Animated transitions
 * - Percentage display
 * - Category label
 */
class ThreatLevelIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var threatLevel: Float = 0f // 0.0 - 1.0
    private var animatedLevel: Float = 0f
    private var threatCategory: ThreatCategory = ThreatCategory.LOW

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(8f)
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(8f)
        color = ContextCompat.getColor(context, R.color.emma_grey_dark)
        alpha = 100
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(24f)
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(12f)
        color = ContextCompat.getColor(context, R.color.emma_text_secondary)
    }

    private val arcBounds = RectF()
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val padding = dpToPx(16f)
        val size = minOf(w, h) - padding * 2
        val left = (w - size) / 2f
        val top = (h - size) / 2f

        arcBounds.set(left, top, left + size, top + size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background arc
        canvas.drawArc(arcBounds, 135f, 270f, false, backgroundArcPaint)

        // Draw threat level arc
        updateArcColor()
        val sweepAngle = animatedLevel * 270f
        canvas.drawArc(arcBounds, 135f, sweepAngle, false, arcPaint)

        // Draw percentage text
        val percentage = (threatLevel * 100).toInt()
        val percentText = "$percentage%"
        textPaint.color = arcPaint.color

        val centerX = width / 2f
        val centerY = height / 2f

        canvas.drawText(percentText, centerX, centerY, textPaint)

        // Draw category label
        val categoryText = when (threatCategory) {
            ThreatCategory.LOW -> "LOW"
            ThreatCategory.MEDIUM -> "MEDIUM"
            ThreatCategory.HIGH -> "HIGH"
            ThreatCategory.CRITICAL -> "CRITICAL"
            ThreatCategory.NUCLEAR -> "NUCLEAR"
        }

        canvas.drawText(categoryText, centerX, centerY + dpToPx(30f), labelPaint)
    }

    fun setThreatLevel(level: Float, category: ThreatCategory, animate: Boolean = true) {
        this.threatLevel = level.coerceIn(0f, 1f)
        this.threatCategory = category

        if (animate) {
            animateToLevel(threatLevel)
        } else {
            animatedLevel = threatLevel
            invalidate()
        }
    }

    private fun animateToLevel(targetLevel: Float) {
        animator?.cancel()

        animator = ValueAnimator.ofFloat(animatedLevel, targetLevel).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                animatedLevel = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateArcColor() {
        arcPaint.color = when (threatCategory) {
            ThreatCategory.LOW -> ContextCompat.getColor(context, R.color.emma_threat_low)
            ThreatCategory.MEDIUM -> ContextCompat.getColor(context, R.color.emma_threat_medium)
            ThreatCategory.HIGH -> ContextCompat.getColor(context, R.color.emma_threat_high)
            ThreatCategory.CRITICAL -> ContextCompat.getColor(context, R.color.emma_threat_critical)
            ThreatCategory.NUCLEAR -> ContextCompat.getColor(context, R.color.emma_threat_nuclear)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
