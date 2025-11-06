package im.molly.translation

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TranslationCache(private val context: Context) {

    private val cacheDir = File(context.filesDir, "translation_cache")
    private val encryptionKey: SecretKey by lazy {
        // In production, derive from Android Keystore
        val keyBytes = ByteArray(32) { 0xAA.toByte() } // Stub key
        SecretKeySpec(keyBytes, "AES")
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getCached(sourceText: String, sourceLang: String, targetLang: String): TranslationResult? {
        val cacheKey = computeCacheKey(sourceText, sourceLang, targetLang)
        val cacheFile = File(cacheDir, cacheKey)

        if (!cacheFile.exists()) {
            return null
        }

        return try {
            val encrypted = cacheFile.readBytes()
            val decrypted = decrypt(encrypted)
            val json = JSONObject(decrypted)

            TranslationResult(
                translatedText = json.getString("text"),
                confidence = json.getDouble("confidence").toFloat(),
                inferenceTimeUs = json.getLong("inference_time"),
                usedNetwork = json.getBoolean("used_network")
            ).also {
                Log.d(TAG, "Cache hit for: ${sourceText.take(50)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache", e)
            null
        }
    }

    fun putCached(sourceText: String, sourceLang: String, targetLang: String, result: TranslationResult) {
        val cacheKey = computeCacheKey(sourceText, sourceLang, targetLang)
        val cacheFile = File(cacheDir, cacheKey)

        try {
            val json = JSONObject().apply {
                put("text", result.translatedText)
                put("confidence", result.confidence.toDouble())
                put("inference_time", result.inferenceTimeUs)
                put("used_network", result.usedNetwork)
                put("timestamp", System.currentTimeMillis())
            }

            val encrypted = encrypt(json.toString())
            cacheFile.writeBytes(encrypted)

            Log.d(TAG, "Cached translation for: ${sourceText.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing cache", e)
        }
    }

    private fun computeCacheKey(sourceText: String, sourceLang: String, targetLang: String): String {
        val input = "$sourceLang|$targetLang|$sourceText"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(data: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) { 0x11.toByte() } // Stub IV, use random in production
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec)
        return iv + cipher.doFinal(data.toByteArray())
    }

    private fun decrypt(data: ByteArray): String {
        val iv = data.sliceArray(0 until 12)
        val ciphertext = data.sliceArray(12 until data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec)
        return String(cipher.doFinal(ciphertext))
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Cache cleared")
    }

    companion object {
        private const val TAG = "TranslationCache"
    }
}
