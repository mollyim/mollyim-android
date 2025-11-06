package im.molly.translation

import android.content.Context
import android.util.Log
import java.io.File

class TranslationEngine private constructor(private val context: Context) {

    private var initialized = false

    init {
        try {
            System.loadLibrary("molly_translation")
            Log.d(TAG, "Translation native library loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load translation library", e)
        }
    }

    fun initialize(modelPath: String): Boolean {
        if (initialized) return true

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found: $modelPath")
            // Continue anyway for stub mode
        }

        initialized = nativeInitialize(modelPath)
        if (initialized) {
            Log.d(TAG, "Translation engine initialized")
        } else {
            Log.e(TAG, "Failed to initialize translation engine")
        }

        return initialized
    }

    fun translate(sourceText: String, sourceLang: String = "da", targetLang: String = "en"): TranslationResult? {
        if (!initialized) {
            Log.w(TAG, "Translation engine not initialized")
            return null
        }

        return try {
            nativeTranslate(sourceText, sourceLang, targetLang)
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            null
        }
    }

    private external fun nativeInitialize(modelPath: String): Boolean
    private external fun nativeTranslate(
        sourceText: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult?

    companion object {
        private const val TAG = "TranslationEngine"

        @Volatile
        private var instance: TranslationEngine? = null

        fun getInstance(context: Context): TranslationEngine {
            return instance ?: synchronized(this) {
                instance ?: TranslationEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
