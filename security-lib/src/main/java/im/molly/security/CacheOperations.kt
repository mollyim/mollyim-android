package im.molly.security

import android.util.Log

object CacheOperations {
    private const val TAG = "CacheOperations"

    init {
        try {
            System.loadLibrary("molly_security")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun poisonCache(intensityPercent: Int) {
        require(intensityPercent in 0..100) { "Intensity must be 0-100" }
        try {
            nativePoisonCache(intensityPercent)
            Log.d(TAG, "Cache poisoned with intensity $intensityPercent%")
        } catch (e: Exception) {
            Log.e(TAG, "Error poisoning cache", e)
        }
    }

    fun fillCacheWithNoise(sizeKb: Int) {
        require(sizeKb > 0) { "Size must be positive" }
        try {
            nativeFillCacheWithNoise(sizeKb)
            Log.d(TAG, "Cache filled with ${sizeKb}KB of noise")
        } catch (e: Exception) {
            Log.e(TAG, "Error filling cache with noise", e)
        }
    }

    private external fun nativePoisonCache(intensity: Int)
    private external fun nativeFillCacheWithNoise(sizeKb: Int)
}
