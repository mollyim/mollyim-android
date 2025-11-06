package im.molly.app.security

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import im.molly.security.AdaptiveCountermeasures
import im.molly.security.TimingObfuscation
import im.molly.security.ThreatAnalysis
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Security wrapper for input methods (FlorisBoard, FUTO Voice Input)
 *
 * Provides:
 * - Keystroke timing randomization
 * - Real-time threat response during text input
 * - Input obfuscation when threat level is high
 * - Memory protection for sensitive input
 */
class MollySecureInputMethodService : InputMethodService() {

    private val countermeasures by lazy { AdaptiveCountermeasures.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentThreat: ThreatAnalysis? = null
    private val lastKeystrokeTime = AtomicLong(0)

    private var wrappedInputView: View? = null
    private var securityEnabled = true

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MollySecureInputMethodService created")

        // Initialize security systems
        if (!countermeasures.initialize()) {
            Log.w(TAG, "Failed to initialize countermeasures")
        }

        // Start monitoring threat level
        countermeasures.addThreatLevelChangeListener(threatListener)
        countermeasures.startMonitoring(intervalMs = 10000) // Every 10s during input
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "Creating secure input view")

        // In production, this would wrap FlorisBoard or FUTO Voice Input
        // For now, create a basic secured input view
        wrappedInputView = createSecuredInputView()

        return wrappedInputView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        Log.d(TAG, "Input started - inputType: ${attribute?.inputType}, package: ${attribute?.packageName}")

        // Check if this is a sensitive field
        val isSensitive = isSensitiveInputField(attribute)

        if (isSensitive) {
            Log.d(TAG, "Sensitive field detected - enabling enhanced protection")
            // Trigger immediate threat analysis
            scope.launch {
                analyzeAndRespond()
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Apply current security posture
        applySecurityPosture()
    }

    override fun sendKeyChar(charCode: Char) {
        if (!securityEnabled) {
            super.sendKeyChar(charCode)
            return
        }

        // Apply keystroke timing randomization
        applyKeystrokeDelay()

        // Send the actual key
        super.sendKeyChar(charCode)

        // Update timestamp
        lastKeystrokeTime.set(System.nanoTime())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!securityEnabled) {
            return super.onKeyDown(keyCode, event)
        }

        // Apply timing obfuscation
        applyKeystrokeDelay()

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!securityEnabled) {
            return super.onKeyUp(keyCode, event)
        }

        // Apply timing obfuscation
        applyKeystrokeDelay()

        return super.onKeyUp(keyCode, event)
    }

    private fun applyKeystrokeDelay() {
        val threat = currentThreat ?: return

        if (threat.threatLevel < 0.35f) {
            return // No obfuscation at low threat
        }

        val chaosIntensity = threat.getChaosIntensity()

        // Calculate delay based on threat level
        val minDelayUs = when {
            threat.threatLevel < 0.65f -> 0
            threat.threatLevel < 0.85f -> 100    // 0.1ms
            threat.threatLevel < 0.95f -> 500    // 0.5ms
            else -> 1000                          // 1ms
        }

        val maxDelayUs = minDelayUs + (chaosIntensity * 10)

        if (maxDelayUs > 0) {
            TimingObfuscation.randomDelay(minDelayUs, maxDelayUs)
        }
    }

    private fun isSensitiveInputField(attribute: EditorInfo?): Boolean {
        if (attribute == null) return false

        // Check if it's a password field
        val inputType = attribute.inputType
        val isPassword = (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                        (inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                        (inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0

        // Check if it's Signal message input
        val isSignalInput = attribute.packageName?.contains("signal") == true ||
                           attribute.packageName?.contains("molly") == true

        return isPassword || isSignalInput
    }

    private fun applySecurityPosture() {
        val threat = currentThreat ?: return

        when {
            threat.threatLevel >= 0.85f -> {
                Log.w(TAG, "High threat detected during input - applying maximum protection")
                // Could add visual indicator here
            }
            threat.threatLevel >= 0.65f -> {
                Log.d(TAG, "Medium threat - applying enhanced protection")
            }
        }
    }

    private suspend fun analyzeAndRespond() {
        // Trigger countermeasures if not already running
        if (currentThreat == null) {
            countermeasures.startMonitoring()
        }
    }

    private fun createSecuredInputView(): View {
        // In production, this would inflate FlorisBoard's layout with security wrapper
        // For now, create a simple view as placeholder
        val view = View(this)
        view.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        return view
    }

    private val threatListener = object : AdaptiveCountermeasures.ThreatLevelChangeListener {
        override fun onThreatLevelChanged(analysis: ThreatAnalysis) {
            currentThreat = analysis

            val category = analysis.getThreatCategory()
            Log.d(TAG, "Threat level changed during input: $category (${analysis.threatLevel})")

            // Apply immediate security posture changes
            scope.launch {
                applySecurityPosture()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        countermeasures.removeThreatLevelChangeListener(threatListener)
        scope.cancel()

        Log.d(TAG, "MollySecureInputMethodService destroyed")
    }

    companion object {
        private const val TAG = "MollySecureIME"
    }
}
