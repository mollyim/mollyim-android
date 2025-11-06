package im.molly.security

data class ThreatAnalysis(
    val threatLevel: Float,
    val hypervisorConfidence: Float,
    val timingAnomalyDetected: Boolean,
    val cacheAnomalyDetected: Boolean,
    val perfCounterBlocked: Boolean,
    val memoryAnomalyDetected: Boolean,
    val analysisTimestamp: Long
) {
    fun getThreatCategory(): ThreatCategory {
        return when {
            threatLevel < 0.35f -> ThreatCategory.LOW
            threatLevel < 0.65f -> ThreatCategory.MEDIUM
            threatLevel < 0.85f -> ThreatCategory.HIGH
            threatLevel < 0.95f -> ThreatCategory.CRITICAL
            else -> ThreatCategory.NUCLEAR
        }
    }

    fun getChaosIntensity(): Int {
        return when (getThreatCategory()) {
            ThreatCategory.LOW -> 10
            ThreatCategory.MEDIUM -> 60
            ThreatCategory.HIGH -> 100
            ThreatCategory.CRITICAL -> 150
            ThreatCategory.NUCLEAR -> 200
        }
    }

    fun getDecoyRatio(): Int {
        return when (getThreatCategory()) {
            ThreatCategory.LOW -> 10
            ThreatCategory.MEDIUM -> 30
            ThreatCategory.HIGH -> 50
            ThreatCategory.CRITICAL -> 70
            ThreatCategory.NUCLEAR -> 90
        }
    }

    fun shouldEnableMemoryProtection(): Boolean = threatLevel >= 0.35f
    fun shouldEnableCachePoisoning(): Boolean = threatLevel >= 0.65f
    fun shouldEnableNetworkObfuscation(): Boolean = threatLevel >= 0.85f
}

enum class ThreatCategory {
    LOW,      // 0-35%
    MEDIUM,   // 35-65%
    HIGH,     // 65-85%
    CRITICAL, // 85-95%
    NUCLEAR   // 95-100%
}
