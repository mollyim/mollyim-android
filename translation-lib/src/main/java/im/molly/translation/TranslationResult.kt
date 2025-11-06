package im.molly.translation

data class TranslationResult(
    val translatedText: String,
    val confidence: Float,
    val inferenceTimeUs: Long,
    val usedNetwork: Boolean
)
