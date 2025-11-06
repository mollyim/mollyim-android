package im.molly.security

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AdaptiveCountermeasures private constructor() {

    private val detector = EL2Detector.getInstance()
    private val isRunning = AtomicBoolean(false)
    private val currentThreat = AtomicReference<ThreatAnalysis?>(null)
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val listeners = mutableListOf<ThreatLevelChangeListener>()

    fun initialize(): Boolean {
        Log.d(TAG, "Initializing adaptive countermeasures")
        return detector.initialize()
    }

    fun startMonitoring(intervalMs: Long = 5000) {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Starting threat monitoring with ${intervalMs}ms interval")

            monitoringJob = scope.launch {
                while (isActive && isRunning.get()) {
                    try {
                        analyzeAndRespond()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during monitoring cycle", e)
                    }

                    delay(intervalMs)
                }
            }
        }
    }

    fun stopMonitoring() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Stopping threat monitoring")
            monitoringJob?.cancel()
            monitoringJob = null
        }
    }

    private suspend fun analyzeAndRespond() {
        val analysis = withContext(Dispatchers.Default) {
            detector.analyzeThreat()
        } ?: return

        val previousThreat = currentThreat.getAndSet(analysis)
        val category = analysis.getThreatCategory()

        Log.d(TAG, "Threat analysis: level=${analysis.threatLevel}, category=$category")

        // Notify listeners if threat level changed significantly
        if (previousThreat == null ||
            previousThreat.getThreatCategory() != analysis.getThreatCategory()) {
            notifyThreatLevelChanged(analysis)
        }

        // Apply countermeasures based on threat level
        applyCountermeasures(analysis)
    }

    private suspend fun applyCountermeasures(analysis: ThreatAnalysis) {
        val chaosIntensity = analysis.getChaosIntensity()
        val decoyRatio = analysis.getDecoyRatio()

        Log.d(TAG, "Applying countermeasures: chaos=$chaosIntensity%, decoy=$decoyRatio%")

        withContext(Dispatchers.Default) {
            // Apply timing obfuscation
            launch {
                TimingObfuscation.addTimingNoise(chaosIntensity / 2)
            }

            // Apply memory protection if needed
            if (analysis.shouldEnableMemoryProtection()) {
                launch {
                    val decoySize = when (analysis.getThreatCategory()) {
                        ThreatCategory.MEDIUM -> 10
                        ThreatCategory.HIGH -> 25
                        ThreatCategory.CRITICAL -> 50
                        ThreatCategory.NUCLEAR -> 100
                        else -> 0
                    }
                    if (decoySize > 0) {
                        MemoryScrambler.createDecoyPatterns(decoySize)
                    }
                }
            }

            // Apply cache poisoning if needed
            if (analysis.shouldEnableCachePoisoning()) {
                launch {
                    val cacheIntensity = (chaosIntensity / 2).coerceIn(10, 100)
                    CacheOperations.poisonCache(cacheIntensity)
                }
            }

            // At maximum threat, fill RAM periodically
            if (analysis.getThreatCategory() == ThreatCategory.NUCLEAR) {
                launch {
                    MemoryScrambler.fillAvailableRAM(30)
                }
            }
        }
    }

    fun getCurrentThreat(): ThreatAnalysis? = currentThreat.get()

    fun addThreatLevelChangeListener(listener: ThreatLevelChangeListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeThreatLevelChangeListener(listener: ThreatLevelChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyThreatLevelChanged(analysis: ThreatAnalysis) {
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    listener.onThreatLevelChanged(analysis)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }

    fun shutdown() {
        stopMonitoring()
        scope.cancel()
    }

    interface ThreatLevelChangeListener {
        fun onThreatLevelChanged(analysis: ThreatAnalysis)
    }

    companion object {
        private const val TAG = "AdaptiveCountermeasures"

        @Volatile
        private var instance: AdaptiveCountermeasures? = null

        fun getInstance(): AdaptiveCountermeasures {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveCountermeasures().also { instance = it }
            }
        }
    }
}
