# EMMA Security Audit Checklist

**Version:** 1.0
**Date:** November 6, 2025
**Scope:** Production hardening Phase 4
**Auditor:** [To be assigned]

---

## 1. JNI Security Audit

### 1.1 Array Handling

- [ ] **All JNI methods validate array lengths before access**
  - `nativeGenerateKeypair()` - No input arrays
  - `nativeEncapsulate()` - Validates public key size
  - `nativeDecapsulate()` - Validates ciphertext and secret key sizes
  - `nativeSign()` - Validates secret key size
  - `nativeVerify()` - Validates signature and public key sizes

- [ ] **No buffer overflows in byte array copying**
  - `Get/SetByteArrayRegion` uses correct lengths
  - `NewByteArray` allocations match expected sizes
  - No off-by-one errors in array indexing

- [ ] **Proper GetByteArrayElements/ReleaseByteArrayElements pairing**
  - Every `GetByteArrayElements` has matching `Release`
  - Release uses `JNI_ABORT` for read-only arrays
  - No double-release vulnerabilities

### 1.2 Exception Handling

- [ ] **All native exceptions caught and converted**
  - C++ `std::exception` caught in JNI layer
  - Proper logging before returning null
  - No uncaught exceptions propagated to Java

- [ ] **Java exceptions checked after JNI calls**
  - `ExceptionCheck()` called after risky operations
  - Exceptions cleared appropriately
  - Resources cleaned up on exception path

### 1.3 Memory Management

- [ ] **No memory leaks in JNI transitions**
  - All `NewByteArray` results tracked
  - Local references deleted when done
  - Global references used correctly for cached objects

- [ ] **Proper cleanup of local references**
  - DeleteLocalRef called for temporary objects
  - EnsureLocalCapacity used for loops
  - No reference table overflow

- [ ] **No dangling pointers**
  - Array elements released before vector destruction
  - No use-after-free in JNI callbacks

### 1.4 Thread Safety

- [ ] **Thread-safe native code**
  - No global mutable state without synchronization
  - liboqs calls are thread-safe (verify documentation)
  - Proper locking for shared resources

- [ ] **JNI attachments handled correctly**
  - JNIEnv not shared between threads
  - AttachCurrentThread/DetachCurrentThread paired
  - No race conditions in native callbacks

### 1.5 Input Validation

- [ ] **All inputs validated before use**
  - Null checks for all jbyteArray parameters
  - Size validation before array access
  - Range checks for integer parameters

- [ ] **No integer overflow vulnerabilities**
  - jsize conversions checked
  - Array length arithmetic safe
  - No wraparound in size calculations

---

## 2. Native Code Security Audit

### 2.1 Memory Safety

- [ ] **No use-after-free vulnerabilities**
  - All heap allocations tracked
  - No double-free conditions
  - RAII patterns used correctly

- [ ] **No buffer overflows**
  - All array accesses bounds-checked
  - std::vector used instead of raw arrays
  - No strcpy/sprintf - use safe alternatives

- [ ] **Proper initialization**
  - All variables initialized before use
  - No uninitialized memory reads
  - Proper constructor initialization lists

### 2.2 liboqs Integration

- [ ] **Correct liboqs API usage**
  - OQS_STATUS checked after every call
  - Buffer sizes match OQS_KEM/OQS_SIG constants
  - No assumptions about internal behavior

- [ ] **Error handling**
  - All OQS_ERROR conditions handled
  - Proper cleanup on error paths
  - No resource leaks on failure

- [ ] **Thread safety with liboqs**
  - Verify liboqs thread-safety guarantees
  - No shared state between calls
  - Document any threading restrictions

### 2.3 Secure Memory Handling

- [ ] **Sensitive data wiped after use**
  - Secret keys zeroed before deallocation
  - Shared secrets cleared from memory
  - std::fill or memset_s used (not compiler-optimizable memset)

- [ ] **No sensitive data in logs**
  - Secret keys never logged
  - Shared secrets never logged
  - Only metadata (sizes, status) logged in production

- [ ] **Constant-time operations where needed**
  - Key comparisons are constant-time
  - No timing leaks in crypto operations
  - liboqs provides constant-time guarantees

### 2.4 Integer Safety

- [ ] **No integer overflow**
  - size_t arithmetic checked
  - No wraparound in calculations
  - Multiplication checked before allocation

- [ ] **Proper type conversions**
  - size_t ↔ jsize conversions safe
  - No truncation of large values
  - Signed/unsigned conversions correct

---

## 3. Cryptographic Implementation Audit

### 3.1 ML-KEM-1024 Implementation

- [ ] **Correct FIPS 203 compliance**
  - Using OQS_KEM_ml_kem_1024_* functions
  - Not using deprecated Kyber APIs
  - Verify liboqs version >= 0.11.0

- [ ] **Key generation**
  - Sufficient entropy source (liboqs internal RNG)
  - Proper key size validation
  - No weak key generation

- [ ] **Encapsulation**
  - Public key validated before use
  - Ciphertext size checked
  - Shared secret properly generated

- [ ] **Decapsulation**
  - Ciphertext and secret key validated
  - Constant-time operation (liboqs guarantee)
  - No timing side-channels

### 3.2 ML-DSA-87 Implementation

- [ ] **Correct FIPS 204 compliance**
  - Using OQS_SIG_ml_dsa_87_* functions
  - Not using deprecated Dilithium APIs
  - Verify liboqs version >= 0.11.0

- [ ] **Signing**
  - Secret key validated
  - Message hashing correct
  - Signature size handling proper

- [ ] **Verification**
  - Signature validated before verification
  - Public key validated
  - Rejection of invalid signatures guaranteed

### 3.3 AES-256-GCM Usage

- [ ] **Proper AES-GCM usage**
  - 96-bit nonces generated uniquely
  - 256-bit keys from ML-KEM shared secret
  - Authentication tags verified before decryption

- [ ] **Nonce uniqueness**
  - Never reuse nonce with same key
  - Counter-based or random generation
  - Collision resistance verified

- [ ] **Key derivation**
  - HKDF-SHA256 for key derivation
  - Proper salt and info parameters
  - Sufficient output key material

### 3.4 Random Number Generation

- [ ] **liboqs RNG used correctly**
  - OQS internal RNG for crypto operations
  - /dev/urandom for non-crypto randomness
  - No weak PRNG for security-critical ops

- [ ] **No custom RNG implementations**
  - All randomness from liboqs or /dev/urandom
  - No seeding of weak generators
  - No deterministic "random" for testing in production

---

## 4. Build System Security

### 4.1 Dependency Management

- [ ] **liboqs source verified**
  - Downloaded from official GitHub repository
  - Git tag verified (0.11.0)
  - SHA-256 checksum verified (optional)

- [ ] **No untrusted dependencies**
  - Only liboqs and OpenSSL used
  - No random GitHub dependencies
  - All dependencies auditable

### 4.2 Build Configuration

- [ ] **Appropriate security flags**
  - `-fstack-protector-strong` enabled
  - `-D_FORTIFY_SOURCE=2` enabled
  - Position-independent code (-fPIC)
  - No `-fno-stack-protector` or dangerous flags

- [ ] **Optimization safe for crypto**
  - `-O3` appropriate for release
  - No `-ffast-math` that breaks crypto
  - liboqs optimizations enabled

### 4.3 Symbol Visibility

- [ ] **Only necessary symbols exported**
  - JNI functions exported
  - Internal functions hidden
  - No unnecessary API surface

- [ ] **Strip debug symbols in release**
  - Release builds stripped
  - No source paths in binaries
  - Symbol tables minimized

---

## 5. Runtime Security

### 5.1 Error Handling

- [ ] **All errors handled gracefully**
  - No crashes on invalid input
  - Proper error messages (no info leaks)
  - Recovery from OQS errors

- [ ] **No information leakage in errors**
  - Error messages don't reveal internal state
  - No stack traces with sensitive data
  - Generic errors for crypto failures

### 5.2 Resource Management

- [ ] **No resource exhaustion**
  - Memory allocations bounded
  - No unbounded loops
  - Proper cleanup on error paths

- [ ] **File descriptors managed**
  - /dev/urandom opened/closed correctly
  - No fd leaks
  - Proper error handling for file ops

### 5.3 Side-Channel Resistance

- [ ] **Timing attacks mitigated**
  - liboqs provides constant-time operations
  - No data-dependent branching in crypto
  - No variable-time comparisons of secrets

- [ ] **Cache timing attacks mitigated**
  - liboqs implements cache-timing resistance
  - No table lookups indexed by secrets
  - Constant memory access patterns

---

## 6. Testing & Verification

### 6.1 Unit Tests

- [ ] **All crypto operations tested**
  - Key generation success
  - Encapsulation/decapsulation correctness
  - Signing/verification correctness
  - Error handling tested

- [ ] **Edge cases covered**
  - Invalid key sizes rejected
  - Null inputs handled
  - Maximum-size messages handled

### 6.2 Integration Tests

- [ ] **End-to-end crypto tested**
  - Full key exchange verified
  - Sign and verify integration tested
  - Hybrid crypto system tested

- [ ] **Performance benchmarks**
  - Meet performance targets
  - No performance regression
  - Memory usage acceptable

### 6.3 Security Tests

- [ ] **Fuzzing results analyzed**
  - No crashes found
  - All exceptions handled
  - Edge cases discovered and fixed

- [ ] **Memory leak detection**
  - Valgrind/ASan clean
  - No leaks in normal operation
  - No leaks in error paths

---

## 7. Documentation

### 7.1 Code Documentation

- [ ] **All public APIs documented**
  - Kotlin KDoc for public methods
  - C++ Doxygen for public functions
  - Usage examples provided

- [ ] **Security considerations documented**
  - Threat model documented
  - Limitations clearly stated
  - Proper usage guidelines

### 7.2 Deployment Documentation

- [ ] **Build instructions complete**
  - liboqs integration documented
  - NDK version specified
  - Build flags explained

- [ ] **Runtime requirements documented**
  - Minimum Android version
  - Required permissions
  - Performance characteristics

---

## 8. Compliance

### 8.1 Cryptographic Standards

- [ ] **FIPS 203 compliance (ML-KEM-1024)**
  - liboqs implements FIPS 203
  - Parameter sets correct
  - Test vectors pass

- [ ] **FIPS 204 compliance (ML-DSA-87)**
  - liboqs implements FIPS 204
  - Parameter sets correct
  - Test vectors pass

### 8.2 Open Source Compliance

- [ ] **License compliance**
  - liboqs MIT license compatible
  - OpenSSL license compatible
  - All licenses documented

- [ ] **Attribution correct**
  - liboqs authors credited
  - NIST standards referenced
  - Third-party code attributed

---

## 9. Audit Results

### Critical Issues
- [ ] None found / List issues

### High Priority Issues
- [ ] None found / List issues

### Medium Priority Issues
- [ ] None found / List issues

### Low Priority Issues
- [ ] None found / List issues

### Recommendations
- [ ] List recommendations

---

## 10. Sign-off

**Auditor:** ______________________  **Date:** ______________________

**Signature:** ______________________

**Developer Response:** ______________________

**Resolution Date:** ______________________

---

**Audit Status:**
- [ ] Not Started
- [ ] In Progress
- [ ] Complete - No Issues
- [ ] Complete - Issues Found
- [ ] Complete - Issues Resolved

**Production Readiness:**
- [ ] ❌ NOT READY
- [ ] ⚠️ CONDITIONALLY READY (with mitigations)
- [ ] ✅ PRODUCTION READY
