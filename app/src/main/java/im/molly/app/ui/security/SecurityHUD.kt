package im.molly.app.ui.security

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import im.molly.app.security.SecurityManager
import im.molly.security.ThreatAnalysis
import im.molly.security.ThreatCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.thoughtcrime.securesms.R

/**
 * Military-Spec Security HUD Overlay
 *
 * Displays real-time security metrics:
 * - Threat level indicator
 * - Active countermeasures
 * - Chaos intensity
 * - Security status
 */
class SecurityHUD @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val securityManager = SecurityManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var threatIndicator: ThreatLevelIndicator
    private lateinit var statusText: TextView
    private lateinit var chaosText: TextView
    private lateinit var countermeasuresText: TextView
    private lateinit var timestampText: TextView

    init {
        inflate(context, R.layout.emma_security_hud, this)
        setupViews()
        startMonitoring()

        // Apply MIL-SPEC styling
        radius = resources.getDimension(R.dimen.emma_card_radius)
        cardElevation = resources.getDimension(R.dimen.emma_card_elevation)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.emma_hud_bg))

        // Add border effect
        strokeWidth = dpToPx(1f).toInt()
        strokeColor = ContextCompat.getColor(context, R.color.emma_hud_border)
    }

    private fun setupViews() {
        threatIndicator = findViewById(R.id.emma_threat_indicator)
        statusText = findViewById(R.id.emma_status_text)
        chaosText = findViewById(R.id.emma_chaos_text)
        countermeasuresText = findViewById(R.id.emma_countermeasures_text)
        timestampText = findViewById(R.id.emma_timestamp_text)
    }

    private fun startMonitoring() {
        scope.launch {
            securityManager.threatLevel.collect { analysis ->
                analysis?.let { updateDisplay(it) }
            }
        }
    }

    private fun updateDisplay(analysis: ThreatAnalysis) {
        val category = analysis.getThreatCategory()
        val percentage = (analysis.threatLevel * 100).toInt()
        val chaosIntensity = analysis.getChaosIntensity()

        // Update threat indicator
        threatIndicator.setThreatLevel(analysis.threatLevel, category, animate = true)

        // Update status text
        val statusColor = getThreatColor(category)
        statusText.apply {
            text = context.getString(R.string.emma_threat_level, percentage)
            setTextColor(statusColor)
        }

        // Update chaos intensity
        chaosText.text = context.getString(R.string.emma_chaos_intensity, chaosIntensity)

        // Update active countermeasures list
        val countermeasures = buildList {
            if (analysis.shouldEnableMemoryProtection()) add("MEM-PROTECT")
            if (analysis.shouldEnableCachePoisoning()) add("CACHE-POISON")
            if (analysis.shouldEnableNetworkObfuscation()) add("NET-OBFUSC")
            if (analysis.timingAnomalyDetected) add("TIMING-ANOM")
            if (analysis.cacheAnomalyDetected) add("CACHE-ANOM")
        }

        countermeasuresText.text = if (countermeasures.isEmpty()) {
            "NO ACTIVE COUNTERMEASURES"
        } else {
            countermeasures.joinToString(" • ")
        }

        // Update timestamp
        val timestamp = System.currentTimeMillis()
        timestampText.text = formatTimestamp(timestamp)
    }

    private fun getThreatColor(category: ThreatCategory): Int {
        return ContextCompat.getColor(context, when (category) {
            ThreatCategory.LOW -> R.color.emma_threat_low
            ThreatCategory.MEDIUM -> R.color.emma_threat_medium
            ThreatCategory.HIGH -> R.color.emma_threat_high
            ThreatCategory.CRITICAL -> R.color.emma_threat_critical
            ThreatCategory.NUCLEAR -> R.color.emma_threat_nuclear
        })
    }

    private fun formatTimestamp(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        return format.format(date)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SecurityHUD"
    }
}
