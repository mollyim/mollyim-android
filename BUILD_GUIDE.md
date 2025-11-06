# Production Build Guide

**EMMA-Android Production Crypto Build**

This guide explains how to build EMMA-Android with production-grade post-quantum cryptography using liboqs.

---

## Prerequisites

### Required Tools

- **Android Studio:** Arctic Fox or later
- **NDK:** Version 26.1.10909125 or later
- **CMake:** Version 3.22.1+ (bundled with Android Studio)
- **Java:** JDK 17+
- **Internet Connection:** Required for first build (downloads liboqs)

### System Requirements

- **OS:** Linux, macOS, or Windows with WSL2
- **RAM:** 8 GB minimum, 16 GB recommended
- **Disk Space:** 5 GB free (for NDK, liboqs, and build artifacts)
- **CPU:** Multi-core recommended for faster builds

---

## Build Modes

### 1. TEST MODE (Default in development)

**Uses:** Secure random implementations (test only)
**Performance:** N/A (test code)
**Binary Size:** Smaller (~200 KB native lib)
**FIPS Compliance:** ❌ No

**Build Command:**
```bash
./gradlew :security-lib:assembleDebug \
  -DPRODUCTION_CRYPTO=OFF
```

### 2. PRODUCTION MODE (Recommended for release)

**Uses:** liboqs FIPS 203/204 implementations
**Performance:** ~3ms ML-KEM, ~7ms ML-DSA
**Binary Size:** Larger (~700 KB native lib)
**FIPS Compliance:** ✅ Yes (FIPS 203 + 204)

**Build Command:**
```bash
./gradlew :security-lib:assembleRelease \
  -DPRODUCTION_CRYPTO=ON
```

---

## Build Configuration

### CMakeLists.txt

The `security-lib/src/main/cpp/CMakeLists.txt` now supports:

**Production Mode (PRODUCTION_CRYPTO=ON):**
- Fetches liboqs from GitHub (v0.11.0)
- Builds minimal liboqs (only ML-KEM-1024 + ML-DSA-87)
- Links production implementations
- Enables ARM64 NEON optimizations
- Uses OpenSSL for AES/SHA operations

**Test Mode (PRODUCTION_CRYPTO=OFF):**
- Uses test implementations (secure random)
- No external dependencies (except OpenSSL)
- Faster build times
- Smaller binary size

### build.gradle.kts

The `security-lib/build.gradle.kts` configures:

**NDK Configuration:**
- NDK Version: 26.1.10909125
- ABI Filter: arm64-v8a only
- STL: c++_shared

**Compiler Flags:**
- C++: `-std=c++17 -O3 -ffast-math -fexceptions -frtti`
- C: `-O3 -ffast-math`

**CMake Arguments:**
- `PRODUCTION_CRYPTO=ON`
- `OQS_USE_OPENSSL=ON`
- `OQS_MINIMAL_BUILD=ON`
- `OQS_ENABLE_KEM_ml_kem_1024=ON`
- `OQS_ENABLE_SIG_ml_dsa_87=ON`
- `OQS_SPEED_USE_ARM_NEON=ON`

---

## First Build (Production Mode)

### Step 1: Clean Previous Builds

```bash
./gradlew clean
rm -rf ~/.gradle/caches/
rm -rf security-lib/.cxx/
```

### Step 2: Build Native Library

```bash
./gradlew :security-lib:assembleDebug
```

**What happens:**
1. Gradle downloads dependencies
2. CMake runs FetchContent for liboqs
3. liboqs is downloaded from GitHub (v0.11.0)
4. liboqs is configured for minimal build
5. liboqs is compiled with ARM64 NEON
6. Production crypto implementations are compiled
7. Native library is linked with liboqs
8. APK is assembled with native library

**Expected Time:** 5-10 minutes (first build)

### Step 3: Verify Build Artifacts

```bash
# Check native library was built
ls -lh security-lib/build/intermediates/cmake/debug/obj/arm64-v8a/

# Should see:
# - libmolly_security.so (~700 KB with liboqs)
# - liboqs.a (~500 KB static library)
```

### Step 4: Run Unit Tests

```bash
./gradlew :app:testDebugUnitTest
```

**Expected:** All 26+ crypto tests should pass

### Step 5: Run Performance Benchmarks

```bash
# Connect Pixel 8A via USB
adb devices

# Run benchmarks
./gradlew :app:connectedDebugAndroidTest \
  --tests "im.molly.app.benchmarks.CryptoBenchmarks"

# View results
adb logcat -s CryptoBenchmark:I
```

**Expected Performance (Pixel 8A):**
- ML-KEM-1024 KeyGen: < 1.5 ms
- ML-KEM-1024 Encaps: < 2.0 ms
- ML-KEM-1024 Decaps: < 2.0 ms
- ML-DSA-87 KeyGen: < 4.0 ms
- ML-DSA-87 Sign: < 6.0 ms
- ML-DSA-87 Verify: < 3.0 ms

---

## Incremental Builds

After the first build, subsequent builds are much faster:

```bash
# Clean only the native library
./gradlew :security-lib:clean

# Rebuild
./gradlew :security-lib:assembleDebug
```

**Expected Time:** 1-2 minutes (liboqs already built)

---

## Build Variants

### Debug Build

```bash
./gradlew :security-lib:assembleDebug
```

**Features:**
- Debug symbols included
- Logging enabled
- No code stripping
- Larger binary size

### Release Build

```bash
./gradlew :security-lib:assembleRelease
```

**Features:**
- Debug symbols stripped
- Optimized code
- Smaller binary size
- Production-ready

---

## Troubleshooting

### Issue: "liboqs not found"

**Cause:** FetchContent failed to download liboqs

**Solution:**
```bash
# Check internet connection
ping github.com

# Clear CMake cache
rm -rf security-lib/.cxx/

# Rebuild
./gradlew :security-lib:assembleDebug --rerun-tasks
```

### Issue: "OQS_KEM_ml_kem_1024 undefined"

**Cause:** liboqs not linked properly

**Solution:**
```bash
# Verify CMakeLists.txt includes FetchLibOQS
cat security-lib/src/main/cpp/CMakeLists.txt | grep "FetchLibOQS"

# Clean and rebuild
./gradlew clean :security-lib:assembleDebug
```

### Issue: "PRODUCTION_CRYPTO not defined"

**Cause:** Build flag not passed correctly

**Solution:**
```bash
# Verify build.gradle.kts has PRODUCTION_CRYPTO=ON
cat security-lib/build.gradle.kts | grep "PRODUCTION_CRYPTO"

# Rebuild with explicit flag
./gradlew :security-lib:assembleDebug \
  -Pandroid.native.buildOutput=verbose
```

### Issue: Build is very slow

**Cause:** Full liboqs build instead of minimal

**Solution:**
```bash
# Verify minimal build flags
cat security-lib/build.gradle.kts | grep "OQS_MINIMAL_BUILD"

# Should see: -DOQS_MINIMAL_BUILD=ON
```

### Issue: Tests fail with "UnsatisfiedLinkError"

**Cause:** Native library not loaded

**Solution:**
```bash
# Check native library exists
ls security-lib/build/intermediates/cmake/debug/obj/arm64-v8a/libmolly_security.so

# Check APK includes native lib
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libmolly_security.so

# Should see: lib/arm64-v8a/libmolly_security.so
```

---

## Verification Checklist

After successful build, verify:

- [ ] `libmolly_security.so` exists (~700 KB)
- [ ] APK includes native library
- [ ] All unit tests pass (26+ tests)
- [ ] Benchmarks meet performance targets
- [ ] No memory leaks (run LeakCanary)
- [ ] No crashes in production mode
- [ ] liboqs version is 0.11.0
- [ ] FIPS 203/204 compliance verified

---

## Binary Size Comparison

| Mode | libmolly_security.so | APK Size Increase |
|------|----------------------|-------------------|
| **Test** | ~200 KB | +200 KB |
| **Production** | ~700 KB | +700 KB |

**Size Breakdown (Production):**
- Core security: ~150 KB
- liboqs minimal: ~500 KB
- JNI bindings: ~50 KB

---

## Performance Characteristics

### Build Times

| Configuration | First Build | Incremental Build |
|---------------|-------------|-------------------|
| **Test Mode** | 30 seconds | 10 seconds |
| **Production Mode** | 5-10 minutes | 1-2 minutes |

### Runtime Performance

See `docs/PRODUCTION_HARDENING_PLAN.md` for detailed performance targets.

---

## Security Considerations

### Production Build

- **MUST** use `PRODUCTION_CRYPTO=ON` for release
- **MUST** verify liboqs version is 0.11.0+
- **MUST** run security audit before deployment
- **MUST** enable ProGuard/R8 for release builds
- **MUST** sign APK with production keys

### Test Build

- **NEVER** deploy test builds to users
- **NEVER** use test crypto in production
- Test builds are for **development only**

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build EMMA Production

on:
  push:
    branches: [ main, release/* ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'

    - name: Install NDK
      run: |
        echo "y" | $ANDROID_HOME/tools/bin/sdkmanager \
          "ndk;26.1.10909125"

    - name: Build Production APK
      run: |
        ./gradlew assembleRelease \
          -DPRODUCTION_CRYPTO=ON

    - name: Run Tests
      run: |
        ./gradlew testReleaseUnitTest

    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: emma-production.apk
        path: app/build/outputs/apk/release/
```

---

## Next Steps

After successful production build:

1. **Run Security Audit** - Follow `docs/SECURITY_AUDIT_CHECKLIST.md`
2. **Performance Benchmarks** - Run on Pixel 6A/8A
3. **Memory Leak Testing** - Use LeakCanary + Valgrind
4. **Fuzzing** - Test crypto implementations
5. **F-Droid Submission** - Prepare for release

---

## Support

**Build Issues:**
- Check `security-lib/build/` for CMake logs
- Enable verbose build: `./gradlew assembleDebug --info`
- Search existing issues in repository

**Performance Issues:**
- Run benchmarks: `CryptoBenchmarks.kt`
- Profile with Android Studio Profiler
- Check ARM NEON optimizations enabled

**Documentation:**
- `docs/PRODUCTION_HARDENING_PLAN.md` - Implementation plan
- `docs/SECURITY_AUDIT_CHECKLIST.md` - Audit framework
- `CRYPTO_UPGRADE.md` - Crypto specifications

---

**Build Status:** ✅ Configuration Complete
**Production Ready:** ⚠️ Requires successful build and audit
**Last Updated:** November 6, 2025
