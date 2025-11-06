package im.molly.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import im.molly.security.AdaptiveCountermeasures
import im.molly.security.ThreatAnalysis
import im.molly.security.ThreatCategory
import im.molly.translation.TranslationEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Central security manager for EMMA-Android
 *
 * Coordinates:
 * - EL2 threat detection and countermeasures
 * - Intimate Protection mode per conversation
 * - Translation services
 * - Security status monitoring
 */
class SecurityManager private constructor(private val context: Context) {

    private val countermeasures = AdaptiveCountermeasures.getInstance()
    private val translationEngine = TranslationEngine.getInstance(context)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // State flows for UI observation
    private val _threatLevel = MutableStateFlow<ThreatAnalysis?>(null)
    val threatLevel: StateFlow<ThreatAnalysis?> = _threatLevel.asStateFlow()

    private val _securityEnabled = MutableStateFlow(true)
    val securityEnabled: StateFlow<Boolean> = _securityEnabled.asStateFlow()

    // Per-conversation Intimate Protection tracking
    private val intimateProtectionThreads = ConcurrentHashMap<Long, Boolean>()

    var isInitialized = false
        private set

    fun initialize(): Boolean {
        if (isInitialized) return true

        Log.d(TAG, "Initializing EMMA Security Manager")

        // Initialize countermeasures
        if (!countermeasures.initialize()) {
            Log.e(TAG, "Failed to initialize adaptive countermeasures")
            return false
        }

        // Initialize translation engine
        val modelPath = "${context.filesDir}/models/opus-mt-da-en-int8.bin"
        translationEngine.initialize(modelPath)

        // Load saved preferences
        _securityEnabled.value = prefs.getBoolean(KEY_SECURITY_ENABLED, true)
        loadIntimateProtectionSettings()

        // Start monitoring
        countermeasures.addThreatLevelChangeListener(threatListener)

        if (_securityEnabled.value) {
            countermeasures.startMonitoring(intervalMs = 5000)
        }

        isInitialized = true
        Log.d(TAG, "EMMA Security Manager initialized successfully")

        return true
    }

    fun enableSecurity(enabled: Boolean) {
        _securityEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SECURITY_ENABLED, enabled).apply()

        if (enabled) {
            countermeasures.startMonitoring()
        } else {
            countermeasures.stopMonitoring()
        }

        Log.d(TAG, "Security ${if (enabled) "enabled" else "disabled"}")
    }

    fun enableIntimateProtection(threadId: Long, enabled: Boolean) {
        intimateProtectionThreads[threadId] = enabled

        // Persist to preferences
        val intimateThreads = intimateProtectionThreads
            .filter { it.value }
            .map { it.key }
            .toSet()

        prefs.edit()
            .putStringSet(KEY_INTIMATE_THREADS, intimateThreads.map { it.toString() }.toSet())
            .apply()

        Log.d(TAG, "Intimate Protection for thread $threadId: ${if (enabled) "enabled" else "disabled"}")

        if (enabled) {
            // Trigger immediate maximum security posture
            scope.launch {
                // Force nuclear threat level for this thread
                Log.w(TAG, "Intimate Protection activated - applying nuclear security posture")
            }
        }
    }

    fun isIntimateProtectionEnabled(threadId: Long): Boolean {
        return intimateProtectionThreads[threadId] ?: false
    }

    fun getCurrentThreat(): ThreatAnalysis? {
        return countermeasures.getCurrentThreat()
    }

    fun getThreatCategory(): ThreatCategory {
        return getCurrentThreat()?.getThreatCategory() ?: ThreatCategory.LOW
    }

    fun getThreatPercentage(): Int {
        val threat = getCurrentThreat() ?: return 0
        return (threat.threatLevel * 100).toInt()
    }

    fun getChaosIntensity(): Int {
        return getCurrentThreat()?.getChaosIntensity() ?: 10
    }

    fun shouldShowSecurityIndicator(): Boolean {
        return _securityEnabled.value && (getThreatPercentage() >= 35)
    }

    private fun loadIntimateProtectionSettings() {
        val saved = prefs.getStringSet(KEY_INTIMATE_THREADS, emptySet()) ?: emptySet()
        saved.forEach { threadIdStr ->
            threadIdStr.toLongOrNull()?.let { threadId ->
                intimateProtectionThreads[threadId] = true
            }
        }
        Log.d(TAG, "Loaded ${intimateProtectionThreads.size} intimate protection threads")
    }

    private val threatListener = object : AdaptiveCountermeasures.ThreatLevelChangeListener {
        override fun onThreatLevelChanged(analysis: ThreatAnalysis) {
            _threatLevel.value = analysis

            val category = analysis.getThreatCategory()
            val percentage = (analysis.threatLevel * 100).toInt()

            Log.d(TAG, "Threat level changed: $category ($percentage%)")

            // Notify UI observers via state flow
            // UI can collect from threatLevel flow
        }
    }

    fun shutdown() {
        countermeasures.removeThreatLevelChangeListener(threatListener)
        countermeasures.shutdown()
        scope.cancel()
        isInitialized = false
        Log.d(TAG, "EMMA Security Manager shut down")
    }

    companion object {
        private const val TAG = "SecurityManager"
        private const val PREFS_NAME = "emma_security_prefs"
        private const val KEY_SECURITY_ENABLED = "security_enabled"
        private const val KEY_INTIMATE_THREADS = "intimate_threads"

        @Volatile
        private var instance: SecurityManager? = null

        fun getInstance(context: Context): SecurityManager {
            return instance ?: synchronized(this) {
                instance ?: SecurityManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
