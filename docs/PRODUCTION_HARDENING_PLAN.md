# Phase 4: Production Hardening Plan

**Goal:** Transform EMMA-Android from test implementation to production-ready secure messaging app

**Timeline:** 2-3 weeks
**Status:** In Progress

---

## 1. Cryptographic Library Selection

### Option A: liboqs (RECOMMENDED)

**Pros:**
- ✅ Reference implementation from Open Quantum Safe
- ✅ Full FIPS 203/204 compliance verified
- ✅ Extensive test coverage
- ✅ ARM64 NEON optimizations
- ✅ Active maintenance (Linux Foundation)
- ✅ CMake integration straightforward
- ✅ BSD/MIT license (permissive)

**Cons:**
- ⚠️ Larger binary size (~2 MB)
- ⚠️ Requires building from source for Android

**Integration:**
```cmake
# Download and build liboqs
FetchContent_Declare(
  liboqs
  GIT_REPOSITORY https://github.com/open-quantum-safe/liboqs.git
  GIT_TAG 0.11.0
)
FetchContent_MakeAvailable(liboqs)

target_link_libraries(molly_security PRIVATE oqs)
```

**API:**
```cpp
OQS_KEM_ml_kem_1024_keypair(pk, sk);
OQS_KEM_ml_kem_1024_encaps(ct, ss, pk);
OQS_KEM_ml_kem_1024_decaps(ss, ct, sk);

OQS_SIG_ml_dsa_87_keypair(pk, sk);
OQS_SIG_ml_dsa_87_sign(sig, &sig_len, msg, msg_len, sk);
OQS_SIG_ml_dsa_87_verify(msg, msg_len, sig, sig_len, pk);
```

### Option B: BoringSSL

**Pros:**
- ✅ Google's production crypto library
- ✅ Used in Chrome/Android
- ✅ Excellent ARM optimization
- ✅ Smaller binary size
- ✅ May already be on device

**Cons:**
- ⚠️ ML-KEM/ML-DSA support experimental
- ⚠️ Not guaranteed FIPS 203/204 compliance
- ⚠️ Internal Google APIs may change
- ⚠️ Harder to build for Android NDK

**Decision: Use liboqs** for FIPS compliance guarantee

---

## 2. Build System Setup

### 2.1 Android NDK Configuration

**File:** `security-lib/build.gradle.kts`

```kotlin
android {
    ndkVersion = "26.1.10909125"

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DOQS_USE_OPENSSL=ON",
                    "-DOQS_MINIMAL_BUILD=ON",
                    "-DOQS_ENABLE_KEM_ml_kem_1024=ON",
                    "-DOQS_ENABLE_SIG_ml_dsa_87=ON"
                )
                cFlags += listOf("-O3", "-ffast-math")
                cppFlags += listOf("-O3", "-ffast-math", "-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

### 2.2 CMakeLists.txt Updates

**File:** `security-lib/src/main/cpp/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(molly_security)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O3 -ffast-math")

# Fetch liboqs
include(FetchContent)
FetchContent_Declare(
    liboqs
    GIT_REPOSITORY https://github.com/open-quantum-safe/liboqs.git
    GIT_TAG 0.11.0
)

set(OQS_USE_OPENSSL ON CACHE BOOL "Use OpenSSL")
set(OQS_MINIMAL_BUILD ON CACHE BOOL "Minimal build")
set(OQS_ENABLE_KEM_ml_kem_1024 ON CACHE BOOL "Enable ML-KEM-1024")
set(OQS_ENABLE_SIG_ml_dsa_87 ON CACHE BOOL "Enable ML-DSA-87")

FetchContent_MakeAvailable(liboqs)

add_library(molly_security SHARED
    el2_detector.cpp
    performance_counters.cpp
    cache_operations.cpp
    memory_scrambler.cpp
    timing_obfuscation.cpp
    ml_kem_1024_production.cpp
    ml_dsa_87_production.cpp
    jni_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(crypto-lib crypto)

target_link_libraries(molly_security
    ${log-lib}
    ${android-lib}
    ${crypto-lib}
    oqs
)

target_include_directories(molly_security PRIVATE
    ${liboqs_SOURCE_DIR}/src
)
```

---

## 3. Production Implementation

### 3.1 ML-KEM-1024 Production Implementation

**File:** `security-lib/src/main/cpp/ml_kem_1024_production.cpp`

```cpp
#include "ml_kem_1024.h"
#include <oqs/oqs.h>
#include <android/log.h>

#define TAG "MLKEM1024_PROD"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace molly {
namespace security {

MLKEM1024::KeyPair MLKEM1024::generate_keypair() {
    LOGD("Generating ML-KEM-1024 keypair (liboqs production)");

    KeyPair keypair;
    keypair.public_key.resize(ML_KEM_1024_PUBLIC_KEY_BYTES);
    keypair.secret_key.resize(ML_KEM_1024_SECRET_KEY_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_keypair(
        keypair.public_key.data(),
        keypair.secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 keypair generation failed");
        throw std::runtime_error("ML-KEM-1024 keypair generation failed");
    }

    LOGD("ML-KEM-1024 keypair generated successfully");
    return keypair;
}

MLKEM1024::EncapsulationResult MLKEM1024::encapsulate(
    const std::vector<uint8_t>& public_key
) {
    if (!validate_public_key(public_key)) {
        throw std::invalid_argument("Invalid ML-KEM-1024 public key");
    }

    LOGD("Encapsulating with ML-KEM-1024 (liboqs production)");

    EncapsulationResult result;
    result.ciphertext.resize(ML_KEM_1024_CIPHERTEXT_BYTES);
    result.shared_secret.resize(ML_KEM_1024_SHARED_SECRET_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_encaps(
        result.ciphertext.data(),
        result.shared_secret.data(),
        public_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 encapsulation failed");
        throw std::runtime_error("ML-KEM-1024 encapsulation failed");
    }

    LOGD("ML-KEM-1024 encapsulation successful");
    return result;
}

std::vector<uint8_t> MLKEM1024::decapsulate(
    const std::vector<uint8_t>& ciphertext,
    const std::vector<uint8_t>& secret_key
) {
    if (!validate_ciphertext(ciphertext)) {
        throw std::invalid_argument("Invalid ML-KEM-1024 ciphertext");
    }

    if (!validate_secret_key(secret_key)) {
        throw std::invalid_argument("Invalid ML-KEM-1024 secret key");
    }

    LOGD("Decapsulating with ML-KEM-1024 (liboqs production)");

    std::vector<uint8_t> shared_secret(ML_KEM_1024_SHARED_SECRET_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_decaps(
        shared_secret.data(),
        ciphertext.data(),
        secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 decapsulation failed");
        throw std::runtime_error("ML-KEM-1024 decapsulation failed");
    }

    LOGD("ML-KEM-1024 decapsulation successful");
    return shared_secret;
}

} // namespace security
} // namespace molly
```

### 3.2 ML-DSA-87 Production Implementation

**File:** `security-lib/src/main/cpp/ml_dsa_87_production.cpp`

```cpp
#include "ml_dsa_87.h"
#include <oqs/oqs.h>
#include <android/log.h>

#define TAG "MLDSA87_PROD"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace molly {
namespace security {

MLDSA87::KeyPair MLDSA87::generate_keypair() {
    LOGD("Generating ML-DSA-87 keypair (liboqs production)");

    KeyPair keypair;
    keypair.public_key.resize(ML_DSA_87_PUBLIC_KEY_BYTES);
    keypair.secret_key.resize(ML_DSA_87_SECRET_KEY_BYTES);

    OQS_STATUS status = OQS_SIG_ml_dsa_87_keypair(
        keypair.public_key.data(),
        keypair.secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-DSA-87 keypair generation failed");
        throw std::runtime_error("ML-DSA-87 keypair generation failed");
    }

    LOGD("ML-DSA-87 keypair generated successfully");
    return keypair;
}

std::vector<uint8_t> MLDSA87::sign(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& secret_key
) {
    if (!validate_secret_key(secret_key)) {
        throw std::invalid_argument("Invalid ML-DSA-87 secret key");
    }

    LOGD("Signing message with ML-DSA-87 (liboqs production, %zu bytes)",
         message.size());

    std::vector<uint8_t> signature(ML_DSA_87_SIGNATURE_BYTES);
    size_t signature_length = signature.size();

    OQS_STATUS status = OQS_SIG_ml_dsa_87_sign(
        signature.data(),
        &signature_length,
        message.data(),
        message.size(),
        secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-DSA-87 signing failed");
        throw std::runtime_error("ML-DSA-87 signing failed");
    }

    signature.resize(signature_length);
    LOGD("ML-DSA-87 signature created (%zu bytes)", signature_length);
    return signature;
}

bool MLDSA87::verify(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& signature,
    const std::vector<uint8_t>& public_key
) {
    if (!validate_signature(signature)) {
        LOGE("Invalid ML-DSA-87 signature size");
        return false;
    }

    if (!validate_public_key(public_key)) {
        LOGE("Invalid ML-DSA-87 public key size");
        return false;
    }

    LOGD("Verifying ML-DSA-87 signature (liboqs production)");

    OQS_STATUS status = OQS_SIG_ml_dsa_87_verify(
        message.data(),
        message.size(),
        signature.data(),
        signature.size(),
        public_key.data()
    );

    bool valid = (status == OQS_SUCCESS);
    LOGD("ML-DSA-87 verification result: %s", valid ? "VALID" : "INVALID");

    return valid;
}

} // namespace security
} // namespace molly
```

---

## 4. Security Audit Checklist

### 4.1 JNI Security Audit

**Checklist:**
- [ ] All JNI methods validate array lengths
- [ ] No buffer overflows in byte array copying
- [ ] Proper exception handling in native code
- [ ] No memory leaks in JNI transitions
- [ ] Proper cleanup of local references
- [ ] Thread-safe native code
- [ ] Secure key material handling (no logging)
- [ ] Constant-time comparisons where needed

### 4.2 Native Code Security Audit

**Checklist:**
- [ ] No use-after-free vulnerabilities
- [ ] No double-free vulnerabilities
- [ ] Bounds checking on all array accesses
- [ ] Integer overflow protection
- [ ] Secure random number generation
- [ ] Secure memory wiping of sensitive data
- [ ] No timing side-channels in crypto operations
- [ ] Proper error handling (no info leaks)

### 4.3 Crypto Implementation Audit

**Checklist:**
- [ ] Using official liboqs API correctly
- [ ] No custom crypto (all via liboqs)
- [ ] Proper key derivation (HKDF-SHA256)
- [ ] Nonce/IV uniqueness guaranteed
- [ ] AES-GCM used correctly
- [ ] Authentication tags verified before decryption
- [ ] Forward secrecy maintained
- [ ] Key rotation implemented

---

## 5. Performance Benchmarking Suite

### 5.1 Crypto Performance Benchmarks

**File:** `app/src/androidTest/java/im/molly/app/benchmarks/CryptoBenchmarks.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class CryptoBenchmarks {

    @Test
    fun benchmark_MLKEM1024_KeyGeneration() {
        val iterations = 100
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            MLKEM1024.generateKeypair()
            val end = System.nanoTime()
            times.add(end - start)
        }

        val avgNs = times.average()
        val avgMs = avgNs / 1_000_000.0

        Log.i("BENCH", "ML-KEM-1024 KeyGen: ${avgMs}ms avg")

        // Assert performance target: < 2ms on Pixel 8A
        assertTrue("KeyGen too slow: ${avgMs}ms", avgMs < 2.0)
    }

    @Test
    fun benchmark_MLKEM1024_Encapsulation() {
        val keypair = MLKEM1024.generateKeypair()!!
        val iterations = 100
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            MLKEM1024.encapsulate(keypair.publicKey)
            val end = System.nanoTime()
            times.add(end - start)
        }

        val avgMs = times.average() / 1_000_000.0
        Log.i("BENCH", "ML-KEM-1024 Encaps: ${avgMs}ms avg")

        assertTrue("Encaps too slow: ${avgMs}ms", avgMs < 2.0)
    }

    @Test
    fun benchmark_MLKEM1024_Decapsulation() {
        val keypair = MLKEM1024.generateKeypair()!!
        val encapResult = MLKEM1024.encapsulate(keypair.publicKey)!!
        val iterations = 100
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            MLKEM1024.decapsulate(encapResult.ciphertext, keypair.secretKey)
            val end = System.nanoTime()
            times.add(end - start)
        }

        val avgMs = times.average() / 1_000_000.0
        Log.i("BENCH", "ML-KEM-1024 Decaps: ${avgMs}ms avg")

        assertTrue("Decaps too slow: ${avgMs}ms", avgMs < 2.0)
    }

    @Test
    fun benchmark_MLDSA87_Signing() {
        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(1024) { it.toByte() }
        val iterations = 100
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            MLDSA87.sign(message, keypair.secretKey)
            val end = System.nanoTime()
            times.add(end - start)
        }

        val avgMs = times.average() / 1_000_000.0
        Log.i("BENCH", "ML-DSA-87 Sign: ${avgMs}ms avg")

        assertTrue("Sign too slow: ${avgMs}ms", avgMs < 5.0)
    }

    @Test
    fun benchmark_MLDSA87_Verification() {
        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(1024) { it.toByte() }
        val signature = MLDSA87.sign(message, keypair.secretKey)!!
        val iterations = 100
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val start = System.nanoTime()
            MLDSA87.verify(message, signature, keypair.publicKey)
            val end = System.nanoTime()
            times.add(end - start)
        }

        val avgMs = times.average() / 1_000_000.0
        Log.i("BENCH", "ML-DSA-87 Verify: ${avgMs}ms avg")

        assertTrue("Verify too slow: ${avgMs}ms", avgMs < 3.0)
    }
}
```

### 5.2 Memory Benchmark

```kotlin
@Test
fun benchmark_MemoryUsage() {
    val runtime = Runtime.getRuntime()

    runtime.gc()
    val before = runtime.totalMemory() - runtime.freeMemory()

    // Generate 100 keypairs
    val keypairs = mutableListOf<MLKEM1024.KeyPair>()
    repeat(100) {
        keypairs.add(MLKEM1024.generateKeypair()!!)
    }

    val after = runtime.totalMemory() - runtime.freeMemory()
    val used = (after - before) / 1024 / 1024 // MB

    Log.i("BENCH", "Memory for 100 ML-KEM keypairs: ${used}MB")

    // Should be ~0.5 MB (100 * 4.7KB)
    assertTrue("Memory usage too high: ${used}MB", used < 1.0)
}
```

---

## 6. Memory Leak Detection

### 6.1 LeakCanary Integration

**File:** `app/build.gradle.kts`

```kotlin
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}
```

### 6.2 Native Memory Leak Testing

**File:** `security-lib/src/main/cpp/memory_leak_detector.h`

```cpp
#ifdef DEBUG_BUILD
class MemoryLeakDetector {
public:
    static void track_allocation(void* ptr, size_t size);
    static void track_deallocation(void* ptr);
    static void report_leaks();
};
#endif
```

---

## 7. Fuzzing Test Suite

### 7.2 Fuzzing Targets

**File:** `security-lib/src/androidTest/cpp/fuzz_ml_kem.cpp`

```cpp
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    if (size < ML_KEM_1024_PUBLIC_KEY_BYTES) {
        return 0;
    }

    std::vector<uint8_t> public_key(
        data,
        data + ML_KEM_1024_PUBLIC_KEY_BYTES
    );

    try {
        auto result = MLKEM1024::encapsulate(public_key);
        // Should either succeed or throw exception
    } catch (...) {
        // Expected for invalid keys
    }

    return 0;
}
```

---

## 8. Performance Targets

### Pixel 8A (Tensor G3)

| Operation | Target | Acceptable |
|-----------|--------|------------|
| ML-KEM-1024 KeyGen | < 0.8 ms | < 1.5 ms |
| ML-KEM-1024 Encaps | < 1.1 ms | < 2.0 ms |
| ML-KEM-1024 Decaps | < 1.3 ms | < 2.0 ms |
| ML-DSA-87 KeyGen | < 2.5 ms | < 4.0 ms |
| ML-DSA-87 Sign | < 4.5 ms | < 6.0 ms |
| ML-DSA-87 Verify | < 2.0 ms | < 3.0 ms |

### Pixel 6A (Tensor G1)

| Operation | Target | Acceptable |
|-----------|--------|------------|
| ML-KEM-1024 Total | < 3.5 ms | < 5.0 ms |
| ML-DSA-87 Total | < 9.0 ms | < 12.0 ms |

---

## 9. Deliverables

### Code
- [ ] liboqs integration in CMakeLists.txt
- [ ] Production ML-KEM-1024 implementation
- [ ] Production ML-DSA-87 implementation
- [ ] Updated JNI bindings (if needed)
- [ ] Performance benchmark suite
- [ ] Memory leak detection
- [ ] Fuzzing test suite

### Documentation
- [ ] Security audit report
- [ ] Performance benchmark report
- [ ] Integration guide
- [ ] Deployment checklist
- [ ] Known limitations document

### Testing
- [ ] All unit tests passing with production crypto
- [ ] Performance benchmarks meet targets
- [ ] No memory leaks detected
- [ ] Fuzzing finds no crashes

---

## 10. Timeline

**Week 1: Integration**
- Day 1-2: liboqs build system setup
- Day 3-4: Production ML-KEM-1024 implementation
- Day 5: Production ML-DSA-87 implementation

**Week 2: Testing & Benchmarking**
- Day 1-2: Performance benchmark suite
- Day 3: Memory leak testing
- Day 4-5: Security audit

**Week 3: Hardening & Documentation**
- Day 1-2: Fuzzing suite
- Day 3: Final security audit
- Day 4-5: Documentation and deployment guide

---

## 11. Success Criteria

✅ **All production crypto using liboqs**
✅ **Performance targets met on Pixel 6A/8A**
✅ **Zero memory leaks detected**
✅ **Security audit completed with no critical issues**
✅ **All tests passing (unit + integration + benchmark)**
✅ **Complete documentation**
✅ **Ready for F-Droid submission**

---

**Status:** Planning complete, ready to begin implementation
**Next Step:** Set up liboqs build system integration
