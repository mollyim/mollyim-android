package im.molly.security

import android.util.Log

object MemoryScrambler {
    private const val TAG = "MemoryScrambler"

    init {
        try {
            System.loadLibrary("molly_security")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    fun secureWipe(data: ByteArray) {
        if (data.isEmpty()) return
        try {
            nativeSecureWipe(data)
            Log.d(TAG, "Securely wiped ${data.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping memory", e)
        }
    }

    fun fillAvailableRAM(fillPercent: Int) {
        require(fillPercent in 1..100) { "Fill percent must be 1-100" }
        try {
            nativeFillAvailableRAM(fillPercent)
            Log.d(TAG, "Filled RAM with $fillPercent% intensity")
        } catch (e: Exception) {
            Log.e(TAG, "Error filling RAM", e)
        }
    }

    fun createDecoyPatterns(sizeMb: Int) {
        require(sizeMb > 0) { "Size must be positive" }
        try {
            nativeCreateDecoyPatterns(sizeMb)
            Log.d(TAG, "Created ${sizeMb}MB of decoy patterns")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating decoy patterns", e)
        }
    }

    private external fun nativeSecureWipe(data: ByteArray)
    private external fun nativeFillAvailableRAM(fillPercent: Int)
    private external fun nativeCreateDecoyPatterns(sizeMb: Int)
}
