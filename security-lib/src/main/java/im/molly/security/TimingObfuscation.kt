package im.molly.security

import android.util.Log

object TimingObfuscation {
    private const val TAG = "TimingObfuscation"

    init {
        try {
            System.loadLibrary("molly_security")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun randomDelay(minUs: Int, maxUs: Int) {
        require(maxUs >= minUs) { "Max must be >= min" }
        try {
            nativeRandomDelay(minUs, maxUs)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding random delay", e)
        }
    }

    fun addTimingNoise(intensityPercent: Int) {
        require(intensityPercent in 0..100) { "Intensity must be 0-100" }
        try {
            nativeAddTimingNoise(intensityPercent)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding timing noise", e)
        }
    }

    fun jitterSleep(baseMs: Int, jitterPercent: Int) {
        require(baseMs >= 0) { "Base ms must be non-negative" }
        require(jitterPercent >= 0) { "Jitter percent must be non-negative" }
        try {
            nativeJitterSleep(baseMs, jitterPercent)
        } catch (e: Exception) {
            Log.e(TAG, "Error with jitter sleep", e)
        }
    }

    inline fun <T> executeWithObfuscation(chaosPercent: Int, block: () -> T): T {
        val preDelayUs = (chaosPercent * 100).coerceIn(0, 10000)
        randomDelay(0, preDelayUs)

        val result = block()

        val postDelayUs = (chaosPercent * 150).coerceIn(0, 15000)
        randomDelay(0, postDelayUs)
        addTimingNoise(chaosPercent / 2)

        return result
    }

    private external fun nativeRandomDelay(minUs: Int, maxUs: Int)
    private external fun nativeAddTimingNoise(intensity: Int)
    private external fun nativeJitterSleep(baseMs: Int, jitterPercent: Int)
}
