package im.molly.translation

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TranslationCoordinator
 *
 * Tests the fallback logic between network and on-device translation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranslationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var mockEngine: TranslationEngine
    private lateinit var mockNetworkClient: NetworkTranslationClient
    private lateinit var mockCache: TranslationCache
    private lateinit var coordinator: TranslationCoordinator

    private val testText = "Hej verden"
    private val sourceLang = "da"
    private val targetLang = "en"

    private val onDeviceResult = TranslationResult(
        translatedText = "[DA->EN] Hej verden",
        confidence = 0.85f,
        inferenceTimeUs = 200000L,
        usedNetwork = false
    )

    private val networkResult = TranslationResult(
        translatedText = "Hello world",
        confidence = 0.95f,
        inferenceTimeUs = 50000L,
        usedNetwork = true
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockEngine = mockk(relaxed = true)
        mockNetworkClient = mockk(relaxed = true)
        mockCache = mockk(relaxed = true)

        // Mock static getInstance methods
        mockkObject(TranslationEngine.Companion)
        every { TranslationEngine.getInstance(any()) } returns mockEngine

        mockkObject(NetworkTranslationClient)
        // Note: NetworkTranslationClient doesn't have getInstance, it's instantiated directly

        mockkObject(TranslationCache.Companion)
        every { TranslationCache.getInstance(any()) } returns mockCache

        // Default mock behaviors
        every { mockEngine.initialize(any()) } returns true
        every { mockEngine.isInitialized() } returns true
        every { mockCache.get(any(), any(), any()) } returns null
        every { mockCache.put(any(), any(), any(), any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test NETWORK_FIRST strategy - network succeeds`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns onDeviceResult
        coEvery { mockNetworkClient.translateViaNetwork(any(), any(), any()) } returns networkResult
        every { mockNetworkClient.hasAvailableServers() } returns true

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNotNull("Result should not be null", result)
        assertEquals("Should use network translation", networkResult.translatedText, result?.translatedText)
        assertTrue("Should mark as network translation", result?.usedNetwork == true)
    }

    @Test
    fun `test NETWORK_FIRST strategy - network fails, fallback to on-device`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns onDeviceResult
        coEvery { mockNetworkClient.translateViaNetwork(any(), any(), any()) } returns null
        every { mockNetworkClient.hasAvailableServers() } returns false

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNotNull("Result should not be null", result)
        assertEquals("Should fallback to on-device translation", onDeviceResult.translatedText, result?.translatedText)
        assertFalse("Should mark as on-device translation", result?.usedNetwork == true)
    }

    @Test
    fun `test ON_DEVICE_FIRST strategy - on-device succeeds`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns onDeviceResult
        every { mockEngine.isInitialized() } returns true

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.ON_DEVICE_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNotNull("Result should not be null", result)
        assertEquals("Should use on-device translation", onDeviceResult.translatedText, result?.translatedText)
        assertFalse("Should mark as on-device translation", result?.usedNetwork == true)

        // Verify network was NOT called
        coVerify(exactly = 0) { mockNetworkClient.translateViaNetwork(any(), any(), any()) }
    }

    @Test
    fun `test ON_DEVICE_FIRST strategy - on-device fails, fallback to network`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns null
        coEvery { mockNetworkClient.translateViaNetwork(any(), any(), any()) } returns networkResult

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.ON_DEVICE_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNotNull("Result should not be null", result)
        assertEquals("Should fallback to network translation", networkResult.translatedText, result?.translatedText)
        assertTrue("Should mark as network translation", result?.usedNetwork == true)
    }

    @Test
    fun `test ON_DEVICE_ONLY strategy - never uses network`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns onDeviceResult
        coEvery { mockNetworkClient.translateViaNetwork(any(), any(), any()) } returns networkResult

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNotNull("Result should not be null", result)
        assertEquals("Should use on-device translation", onDeviceResult.translatedText, result?.translatedText)

        // Verify network was NEVER called
        coVerify(exactly = 0) { mockNetworkClient.translateViaNetwork(any(), any(), any()) }
    }

    @Test
    fun `test ON_DEVICE_ONLY strategy - returns null when on-device fails`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns null

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNull("Result should be null when on-device fails in ON_DEVICE_ONLY mode", result)

        // Verify network was NEVER called even though on-device failed
        coVerify(exactly = 0) { mockNetworkClient.translateViaNetwork(any(), any(), any()) }
    }

    @Test
    fun `test cache hit - no translation performed`() = runTest {
        // Setup
        val cachedResult = TranslationResult(
            translatedText = "Cached: Hello world",
            confidence = 0.99f,
            inferenceTimeUs = 0L,
            usedNetwork = false
        )
        every { mockCache.get(testText, sourceLang, targetLang) } returns cachedResult

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertEquals("Should return cached result", cachedResult.translatedText, result?.translatedText)

        // Verify no translation methods were called
        verify(exactly = 0) { mockEngine.translate(any(), any(), any()) }
        coVerify(exactly = 0) { mockNetworkClient.translateViaNetwork(any(), any(), any()) }
    }

    @Test
    fun `test cache is populated after successful translation`() = runTest {
        // Setup
        every { mockEngine.translate(any(), any(), any()) } returns onDeviceResult

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.ON_DEVICE_FIRST
        )

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify cache was populated
        verify(exactly = 1) {
            mockCache.put(testText, sourceLang, targetLang, onDeviceResult)
        }
    }

    @Test
    fun `test strategy can be changed at runtime`() = runTest {
        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST
        )

        // Verify initial strategy
        assertEquals(
            TranslationCoordinator.TranslationStrategy.NETWORK_FIRST,
            coordinator.getStrategy()
        )

        // Change strategy
        coordinator.setStrategy(TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY)

        // Verify strategy changed
        assertEquals(
            TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY,
            coordinator.getStrategy()
        )
    }

    @Test
    fun `test network discovery stopped when switching to ON_DEVICE_ONLY`() = runTest {
        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST,
            enableNetworkDiscovery = true
        )

        // Change to ON_DEVICE_ONLY
        coordinator.setStrategy(TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY)

        // Verify discovery was stopped
        verify { mockNetworkClient.stopDiscovery() }
    }

    @Test
    fun `test availability checks`() = runTest {
        every { mockEngine.isInitialized() } returns true
        every { mockNetworkClient.hasAvailableServers() } returns true

        coordinator = TranslationCoordinator.getInstance(context)
        coordinator.initialize(
            modelPath = "/test/model.bin",
            translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST
        )

        // Verify availability
        assertTrue("On-device should be available", coordinator.isOnDeviceAvailable())
        assertTrue("Network should be available", coordinator.isNetworkAvailable())
    }

    @Test
    fun `test uninitialized coordinator returns null`() = runTest {
        coordinator = TranslationCoordinator.getInstance(context)
        // Don't call initialize()

        // Execute
        val result = coordinator.translate(testText, sourceLang, targetLang)

        // Verify
        assertNull("Uninitialized coordinator should return null", result)
    }
}
