package im.molly.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Translation coordinator that manages both on-device and network translation
 * with intelligent fallback mechanisms.
 *
 * Features:
 * - Automatic fallback to on-device translation when network is unavailable
 * - Configurable translation strategy (network-first, on-device-first, on-device-only)
 * - Integrated caching with encryption
 * - Network timeout handling
 * - Automatic server discovery
 *
 * Usage:
 * ```
 * val coordinator = TranslationCoordinator.getInstance(context)
 * coordinator.initialize(modelPath, TranslationStrategy.NETWORK_FIRST)
 *
 * val result = coordinator.translate("Hej verden", "da", "en")
 * // Returns network translation if available, falls back to on-device
 * ```
 */
class TranslationCoordinator private constructor(private val context: Context) {

    private val translationEngine: TranslationEngine by lazy {
        TranslationEngine.getInstance(context)
    }

    private val networkClient: NetworkTranslationClient by lazy {
        NetworkTranslationClient(context)
    }

    private val translationCache: TranslationCache by lazy {
        TranslationCache(context)
    }

    private var strategy: TranslationStrategy = TranslationStrategy.NETWORK_FIRST
    private var networkTimeoutMs: Long = DEFAULT_NETWORK_TIMEOUT_MS
    private var initialized: Boolean = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class TranslationStrategy {
        /**
         * Prefer network translation, fallback to on-device if network fails
         * Best for: Users with reliable internet, want fastest/best quality
         */
        NETWORK_FIRST,

        /**
         * Prefer on-device translation, only use network if on-device fails
         * Best for: Privacy-conscious users, users with slow/unreliable internet
         */
        ON_DEVICE_FIRST,

        /**
         * Only use on-device translation, never use network
         * Best for: Maximum privacy, offline-only usage
         */
        ON_DEVICE_ONLY
    }

    /**
     * Initialize the translation system
     *
     * @param modelPath Path to the on-device translation model
     * @param translationStrategy Strategy for choosing between network and on-device
     * @param enableNetworkDiscovery Whether to start mDNS discovery for translation servers
     * @param networkTimeoutMs Timeout for network translation attempts
     * @return true if initialization successful
     */
    fun initialize(
        modelPath: String,
        translationStrategy: TranslationStrategy = TranslationStrategy.NETWORK_FIRST,
        enableNetworkDiscovery: Boolean = true,
        networkTimeoutMs: Long = DEFAULT_NETWORK_TIMEOUT_MS
    ): Boolean {
        if (initialized) {
            Log.d(TAG, "Translation coordinator already initialized")
            return true
        }

        this.strategy = translationStrategy
        this.networkTimeoutMs = networkTimeoutMs

        // Initialize on-device engine
        val engineInit = translationEngine.initialize(modelPath)
        if (!engineInit) {
            Log.e(TAG, "Failed to initialize on-device translation engine")
            // Continue anyway - we might still use network translation
        }

        // Start network discovery if enabled and strategy allows network
        if (enableNetworkDiscovery && strategy != TranslationStrategy.ON_DEVICE_ONLY) {
            try {
                networkClient.startDiscovery()
                Log.d(TAG, "Network translation discovery started")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start network discovery", e)
                // Not a fatal error - we can still use on-device
            }
        }

        initialized = true
        Log.i(TAG, "Translation coordinator initialized with strategy: $strategy")
        return true
    }

    /**
     * Translate text with automatic fallback
     *
     * This is the main translation method that implements the fallback logic.
     * Depending on the configured strategy, it will:
     * - NETWORK_FIRST: Try network, fallback to on-device
     * - ON_DEVICE_FIRST: Try on-device, fallback to network
     * - ON_DEVICE_ONLY: Only use on-device
     *
     * Results are cached automatically.
     *
     * @param text Text to translate
     * @param sourceLang Source language code (e.g., "da")
     * @param targetLang Target language code (e.g., "en")
     * @return TranslationResult or null if all methods fail
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "da",
        targetLang: String = "en"
    ): TranslationResult? {
        if (!initialized) {
            Log.e(TAG, "Translation coordinator not initialized")
            return null
        }

        // Check cache first
        val cached = translationCache.get(text, sourceLang, targetLang)
        if (cached != null) {
            Log.d(TAG, "Translation cache hit")
            return cached
        }

        // Execute translation based on strategy
        val result = when (strategy) {
            TranslationStrategy.NETWORK_FIRST -> translateNetworkFirst(text, sourceLang, targetLang)
            TranslationStrategy.ON_DEVICE_FIRST -> translateOnDeviceFirst(text, sourceLang, targetLang)
            TranslationStrategy.ON_DEVICE_ONLY -> translateOnDeviceOnly(text, sourceLang, targetLang)
        }

        // Cache successful result
        if (result != null) {
            translationCache.put(text, sourceLang, targetLang, result)
        }

        return result
    }

    /**
     * Translate with network-first strategy
     */
    private suspend fun translateNetworkFirst(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult? {
        Log.d(TAG, "Attempting network-first translation")

        // Try network with timeout
        val networkResult = withTimeoutOrNull(networkTimeoutMs) {
            try {
                networkClient.translateViaNetwork(text, sourceLang, targetLang)
            } catch (e: Exception) {
                Log.w(TAG, "Network translation failed", e)
                null
            }
        }

        if (networkResult != null) {
            Log.d(TAG, "Network translation successful")
            return networkResult
        }

        // Network failed or timed out, fallback to on-device
        Log.i(TAG, "Network translation unavailable, falling back to on-device")
        return translateOnDevice(text, sourceLang, targetLang)
    }

    /**
     * Translate with on-device-first strategy
     */
    private suspend fun translateOnDeviceFirst(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult? {
        Log.d(TAG, "Attempting on-device-first translation")

        // Try on-device first
        val onDeviceResult = translateOnDevice(text, sourceLang, targetLang)
        if (onDeviceResult != null) {
            Log.d(TAG, "On-device translation successful")
            return onDeviceResult
        }

        // On-device failed, try network as fallback
        Log.i(TAG, "On-device translation unavailable, falling back to network")
        return withTimeoutOrNull(networkTimeoutMs) {
            try {
                networkClient.translateViaNetwork(text, sourceLang, targetLang)
            } catch (e: Exception) {
                Log.w(TAG, "Network translation failed", e)
                null
            }
        }
    }

    /**
     * Translate with on-device-only strategy
     */
    private suspend fun translateOnDeviceOnly(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult? {
        Log.d(TAG, "Using on-device-only translation")
        return translateOnDevice(text, sourceLang, targetLang)
    }

    /**
     * Perform on-device translation
     */
    private suspend fun translateOnDevice(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult? {
        return withContext(Dispatchers.Default) {
            try {
                translationEngine.translate(text, sourceLang, targetLang)
            } catch (e: Exception) {
                Log.e(TAG, "On-device translation error", e)
                null
            }
        }
    }

    /**
     * Synchronous version of translate for Java compatibility
     */
    fun translateBlocking(
        text: String,
        sourceLang: String = "da",
        targetLang: String = "en"
    ): TranslationResult? {
        return runBlocking {
            translate(text, sourceLang, targetLang)
        }
    }

    /**
     * Change translation strategy at runtime
     */
    fun setStrategy(newStrategy: TranslationStrategy) {
        val oldStrategy = strategy
        strategy = newStrategy
        Log.i(TAG, "Translation strategy changed from $oldStrategy to $newStrategy")

        // Start or stop network discovery based on new strategy
        if (newStrategy == TranslationStrategy.ON_DEVICE_ONLY) {
            networkClient.stopDiscovery()
        } else if (oldStrategy == TranslationStrategy.ON_DEVICE_ONLY) {
            networkClient.startDiscovery()
        }
    }

    /**
     * Get current translation strategy
     */
    fun getStrategy(): TranslationStrategy = strategy

    /**
     * Clear translation cache
     */
    fun clearCache() {
        translationCache.clear()
        Log.d(TAG, "Translation cache cleared")
    }

    /**
     * Check if network translation is available
     */
    fun isNetworkAvailable(): Boolean {
        return networkClient.hasAvailableServers()
    }

    /**
     * Check if on-device translation is available
     */
    fun isOnDeviceAvailable(): Boolean {
        // Simple check - in production might want to verify model is actually loaded
        return translationEngine.isInitialized()
    }

    /**
     * Shutdown the coordinator and release resources
     */
    fun shutdown() {
        networkClient.shutdown()
        scope.cancel()
        initialized = false
        Log.d(TAG, "Translation coordinator shutdown")
    }

    companion object {
        private const val TAG = "TranslationCoordinator"
        private const val DEFAULT_NETWORK_TIMEOUT_MS = 3000L // 3 seconds

        @Volatile
        private var instance: TranslationCoordinator? = null

        fun getInstance(context: Context): TranslationCoordinator {
            return instance ?: synchronized(this) {
                instance ?: TranslationCoordinator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
