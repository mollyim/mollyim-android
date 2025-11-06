# Translation Library - Offline Translation with Automatic Fallback

A hybrid translation system for SWORDCOMM with automatic fallback between network and on-device translation.

## Features

- **Automatic Fallback**: Seamlessly switches between network and on-device translation
- **Multiple Strategies**: Configure translation preference (network-first, on-device-first, on-device-only)
- **Encrypted Caching**: AES-256-GCM encrypted cache for translated content
- **Post-Quantum Security**: Kyber-1024 key exchange for network translation (stub implementation)
- **mDNS Discovery**: Automatic server discovery with zero-configuration
- **Privacy-First**: On-device translation available for complete offline operation

## Architecture

The translation system consists of three main components:

### 1. TranslationCoordinator (Recommended Interface)

The `TranslationCoordinator` manages both network and on-device translation with intelligent fallback logic.

```kotlin
// Initialize with automatic fallback
val coordinator = TranslationCoordinator.getInstance(context)
coordinator.initialize(
    modelPath = "${context.filesDir}/models/opus-mt-da-en-int8.bin",
    translationStrategy = TranslationCoordinator.TranslationStrategy.NETWORK_FIRST,
    enableNetworkDiscovery = true,
    networkTimeoutMs = 3000L
)

// Translate with automatic fallback
lifecycleScope.launch {
    val result = coordinator.translate("Hej verden", "da", "en")
    println("Translation: ${result?.translatedText}")
    println("Used network: ${result?.usedNetwork}")
}
```

### 2. TranslationEngine (On-Device)

Low-level interface for on-device translation using INT8-quantized MarianMT models.

```kotlin
val engine = TranslationEngine.getInstance(context)
engine.initialize("/path/to/model.bin")
val result = engine.translate("Hej verden", "da", "en")
```

**Note**: Currently returns stub translation `[DA->EN] text`. Production implementation requires actual MarianMT model inference.

### 3. NetworkTranslationClient (Server-Based)

Client for encrypted translation via network server with mDNS discovery.

```kotlin
val client = NetworkTranslationClient(context)
client.startDiscovery() // Start mDNS discovery

lifecycleScope.launch {
    val result = client.translateViaNetwork("Hej verden", "da", "en")
}
```

## Translation Strategies

The `TranslationCoordinator` supports three strategies:

### NETWORK_FIRST (Default)

**Use case**: Users with reliable internet connection

- Tries network translation first
- Falls back to on-device if network fails or times out
- Best for quality and speed when network is available

```kotlin
coordinator.setStrategy(TranslationCoordinator.TranslationStrategy.NETWORK_FIRST)
```

**Flow**:
```
Try Network (3s timeout)
    ↓ Success → Return network result
    ↓ Fail/Timeout
Fallback to On-Device
    ↓ Success → Return on-device result
    ↓ Fail → Return null
```

### ON_DEVICE_FIRST

**Use case**: Privacy-conscious users, unreliable network

- Tries on-device translation first
- Falls back to network only if on-device fails
- Minimizes network exposure while maintaining availability

```kotlin
coordinator.setStrategy(TranslationCoordinator.TranslationStrategy.ON_DEVICE_FIRST)
```

**Flow**:
```
Try On-Device
    ↓ Success → Return on-device result
    ↓ Fail
Fallback to Network (3s timeout)
    ↓ Success → Return network result
    ↓ Fail/Timeout → Return null
```

### ON_DEVICE_ONLY

**Use case**: Maximum privacy, offline operation, air-gapped systems

- Only uses on-device translation
- Never attempts network connection
- Automatically stops mDNS discovery

```kotlin
coordinator.setStrategy(TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY)
```

**Flow**:
```
Try On-Device
    ↓ Success → Return on-device result
    ↓ Fail → Return null
```

## Integration with SecurityManager

The translation system is integrated into the `SecurityManager` for app-wide access:

```kotlin
val securityManager = SecurityManager.getInstance(context)
securityManager.initialize()

// Get translation coordinator
val coordinator = securityManager.getTranslationCoordinator()

// Check availability
if (securityManager.isOnDeviceTranslationAvailable()) {
    println("On-device translation ready")
}
if (securityManager.isNetworkTranslationAvailable()) {
    println("Network translation available (${coordinator.getServerCount()} servers)")
}

// Change strategy at runtime
securityManager.setTranslationStrategy(
    TranslationCoordinator.TranslationStrategy.ON_DEVICE_ONLY
)
```

## Caching

All translation results are automatically cached with AES-256-GCM encryption:

```kotlin
// Cache is checked automatically before translation
val result1 = coordinator.translate("Hej", "da", "en") // Performs translation
val result2 = coordinator.translate("Hej", "da", "en") // Returns cached result

// Clear cache if needed
coordinator.clearCache()
```

**Cache Key**: `SHA-256("{sourceLang}|{targetLang}|{sourceText}")`

## Network Translation

### Server Discovery (mDNS)

The system uses mDNS/Bonjour for zero-configuration server discovery:

```kotlin
// Discovery starts automatically on initialization
coordinator.initialize(
    modelPath = modelPath,
    translationStrategy = TranslationStrategy.NETWORK_FIRST,
    enableNetworkDiscovery = true // Enable mDNS discovery
)

// Check discovered servers
if (coordinator.isNetworkAvailable()) {
    println("Translation servers available")
}
```

**Service Type**: `_emma-translate._tcp`

### Server Setup

Run the Python translation server on your local network:

```bash
cd server/
python3 translation_server.py --port 8888
```

The server will:
- Load Helsinki-NLP/opus-mt-da-en MarianMT model
- Advertise via mDNS as `_emma-translate._tcp`
- Accept encrypted translation requests
- Use GPU acceleration if available

See `server/README.md` for detailed server setup.

### Network Security

Network translation uses:
- **Key Exchange**: Kyber-1024 post-quantum key encapsulation (currently stub)
- **Session Encryption**: AES-256-GCM authenticated encryption
- **Key Rotation**: 5-minute session key rotation
- **Forward Secrecy**: New session key for each rotation period

**Current Status**: Key exchange is stubbed with random session keys. Production deployment requires actual Kyber-1024 implementation.

## On-Device Translation

### Model Format

- **Model Type**: MarianMT INT8 quantized
- **Languages**: Danish → English (da-en)
- **Size**: ~100MB (quantized from ~300MB FP32)
- **Path**: `{context.filesDir}/models/opus-mt-da-en-int8.bin`

### Performance

Based on design specifications:

| Device | Backend | Latency |
|--------|---------|---------|
| High-end (SD 888+) | INT8 | 100-200ms |
| Mid-range (SD 7xx) | INT8 | 200-400ms |
| Server (8-core CPU) | FP32 | ~50ms |
| Server (CUDA GPU) | FP32 | ~10ms |

**Current Status**: On-device translation is stubbed (returns `[DA->EN] text`). Actual inference requires implementing MarianMT model loading and execution.

### Model Installation

```bash
# Download translation model (stub implementation)
cd /path/to/SWORDCOMM
./scripts/download_models.sh
```

Models are stored in app private storage: `{context.filesDir}/models/`

## Usage Examples

### Basic Translation

```kotlin
val coordinator = TranslationCoordinator.getInstance(context)
coordinator.initialize(modelPath)

lifecycleScope.launch {
    val result = coordinator.translate(
        text = "Hvordan har du det?",
        sourceLang = "da",
        targetLang = "en"
    )

    result?.let {
        println("Original: Hvordan har du det?")
        println("Translated: ${it.translatedText}")
        println("Confidence: ${it.confidence}")
        println("Time: ${it.inferenceTimeUs}μs")
        println("Network: ${it.usedNetwork}")
    }
}
```

### Synchronous Translation (for Java compatibility)

```kotlin
// Blocks current thread - use carefully
val result = coordinator.translateBlocking("Hej", "da", "en")
```

### Checking Availability Before Translation

```kotlin
when {
    coordinator.isOnDeviceAvailable() -> {
        println("✓ On-device translation ready")
    }
    coordinator.isNetworkAvailable() -> {
        println("✓ Network translation available")
    }
    else -> {
        println("✗ No translation available")
    }
}
```

### Privacy-First Configuration

```kotlin
// For maximum privacy (no network)
coordinator.initialize(
    modelPath = modelPath,
    translationStrategy = TranslationStrategy.ON_DEVICE_ONLY,
    enableNetworkDiscovery = false
)
```

### Network-First Configuration

```kotlin
// For best quality and speed (when online)
coordinator.initialize(
    modelPath = modelPath,
    translationStrategy = TranslationStrategy.NETWORK_FIRST,
    enableNetworkDiscovery = true,
    networkTimeoutMs = 3000L // 3 second timeout
)
```

## Testing

Unit tests are provided for the fallback mechanism:

```bash
# Run translation tests
./gradlew :translation-lib:test

# Run specific test
./gradlew :translation-lib:test --tests TranslationCoordinatorTest
```

### Test Coverage

- Network-first strategy with successful network
- Network-first strategy with network failure → on-device fallback
- On-device-first strategy with successful on-device
- On-device-first strategy with on-device failure → network fallback
- On-device-only strategy (no network fallback)
- Cache hit scenarios
- Strategy runtime switching
- Availability checks

## Implementation Status

### ✅ Completed

- TranslationCoordinator with automatic fallback
- NetworkTranslationClient with mDNS discovery
- TranslationCache with AES-256-GCM encryption
- AES-256-GCM session encryption
- JNI bridge to C++ native code
- Python translation server
- SecurityManager integration
- Unit tests for fallback logic

### ⚠️ Stub/Placeholder

- **On-device model inference**: Returns `[DA->EN] text` instead of actual translation
- **Kyber-1024 key exchange**: Uses random session key instead of actual Kyber-1024
- **Cache encryption key**: Uses hardcoded stub key instead of Android Keystore
- **Model download script**: Placeholder implementation

### 🚧 Future Enhancements

- Additional language pairs (en-da, da-no, etc.)
- Streaming translation for long texts
- Batch translation API
- Translation quality metrics
- Model hot-swapping
- Android Keystore integration for cache encryption
- Actual Kyber-1024 implementation using liboqs

## Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":translation-lib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

## Build Configuration

The translation library uses CMake for native C++ components:

```
translation-lib/
├── src/main/
│   ├── java/im/molly/translation/
│   │   ├── TranslationCoordinator.kt    (Main interface)
│   │   ├── TranslationEngine.kt         (On-device)
│   │   ├── NetworkTranslationClient.kt  (Server-based)
│   │   ├── TranslationCache.kt          (Encrypted cache)
│   │   └── TranslationResult.kt         (Data class)
│   └── cpp/
│       ├── translation_engine.cpp       (Native inference)
│       ├── jni_bridge_translation.cpp   (JNI interface)
│       └── CMakeLists.txt               (Build config)
└── src/test/
    └── java/im/molly/translation/
        └── TranslationCoordinatorTest.kt
```

## Performance Considerations

### Network Timeout

Default timeout is 3 seconds. Adjust based on network conditions:

```kotlin
coordinator.initialize(
    modelPath = modelPath,
    translationStrategy = TranslationStrategy.NETWORK_FIRST,
    networkTimeoutMs = 5000L // 5 seconds for slow networks
)
```

### Battery Impact

- **On-device**: Higher CPU usage, more battery drain
- **Network**: Lower CPU usage, but uses network radio

Consider using `ON_DEVICE_FIRST` on battery power and `NETWORK_FIRST` when charging.

### Memory Usage

- **Model in memory**: ~100MB (INT8 quantized)
- **Cache**: ~1MB per 1000 translations (encrypted)

Clear cache periodically if memory is constrained:

```kotlin
coordinator.clearCache()
```

## Security Considerations

### Data Privacy

- **On-device translation**: Text never leaves device
- **Network translation**: Text sent encrypted to local network server
- **Cache**: All cached translations encrypted with AES-256-GCM

### Network Security

- Use network translation only on trusted networks
- Server runs on local network only (no internet exposure)
- Session keys rotated every 5 minutes
- Consider `ON_DEVICE_ONLY` for sensitive content

### Key Management

**Current**: Stub keys (development only)
**Production**: Requires Android Keystore integration for secure key storage

## Troubleshooting

### "Translation engine not initialized"

Ensure `initialize()` is called before translation:

```kotlin
coordinator.initialize(modelPath)
```

### "No translation servers available"

Network translation requires a server on the local network:

```bash
# Start server on another machine
cd server/
python3 translation_server.py
```

### "Model file not found"

Download and place the model file:

```bash
./scripts/download_models.sh
```

Or check the model path:

```kotlin
val modelPath = "${context.filesDir}/models/opus-mt-da-en-int8.bin"
File(modelPath).exists() // Should be true
```

### On-device translation returns stub text

This is expected. Actual model inference is not yet implemented. The system returns `[DA->EN] text` as a placeholder.

## License

Part of the SWORDCOMM project.

## References

- [MarianMT Models](https://huggingface.co/Helsinki-NLP)
- [Kyber Post-Quantum Cryptography](https://pq-crystals.org/kyber/)
- [Android Network Service Discovery](https://developer.android.com/training/connect-devices-wirelessly/nsd)
