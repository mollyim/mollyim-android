package im.molly.security

import android.util.Log

class EL2Detector private constructor() {

    private var initialized = false

    init {
        try {
            System.loadLibrary("molly_security")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun initialize(): Boolean {
        if (initialized) return true

        initialized = nativeInitialize()
        if (initialized) {
            Log.d(TAG, "EL2 Detector initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize EL2 Detector")
        }
        return initialized
    }

    fun analyzeThreat(): ThreatAnalysis? {
        if (!initialized) {
            Log.w(TAG, "EL2 Detector not initialized")
            return null
        }

        return try {
            nativeAnalyzeThreat()
        } catch (e: Exception) {
            Log.e(TAG, "Error during threat analysis", e)
            null
        }
    }

    private external fun nativeInitialize(): Boolean
    private external fun nativeAnalyzeThreat(): ThreatAnalysis?

    companion object {
        private const val TAG = "EL2Detector"

        @Volatile
        private var instance: EL2Detector? = null

        fun getInstance(): EL2Detector {
            return instance ?: synchronized(this) {
                instance ?: EL2Detector().also { instance = it }
            }
        }
    }
}
