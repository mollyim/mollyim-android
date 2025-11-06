#!/bin/bash
# EMMA-Android Compilation Verification Script
# Verifies build system is ready for production compilation

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================"
echo "EMMA-Android Compilation Test"
echo "========================================"
echo ""

ERRORS=0
WARNINGS=0

# Function to check file exists
check_file() {
    local file=$1
    local description=$2
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $description: $file"
        return 0
    else
        echo -e "${RED}✗${NC} $description: $file (MISSING)"
        ((ERRORS++))
        return 1
    fi
}

# Function to check directory exists
check_dir() {
    local dir=$1
    local description=$2
    if [ -d "$dir" ]; then
        echo -e "${GREEN}✓${NC} $description: $dir"
        return 0
    else
        echo -e "${RED}✗${NC} $description: $dir (MISSING)"
        ((ERRORS++))
        return 1
    fi
}

# Function to check string in file
check_in_file() {
    local file=$1
    local string=$2
    local description=$3
    if grep -q "$string" "$file" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $description"
        return 0
    else
        echo -e "${RED}✗${NC} $description (NOT FOUND in $file)"
        ((ERRORS++))
        return 1
    fi
}

echo "=== 1. Core Build Files ==="
check_file "security-lib/build.gradle.kts" "Gradle build file"
check_file "security-lib/src/main/cpp/CMakeLists.txt" "CMake configuration"
check_file "security-lib/cmake/FetchLibOQS.cmake" "liboqs fetch script"
echo ""

echo "=== 2. Production Crypto Implementations ==="
check_file "security-lib/src/main/cpp/ml_kem_1024_production.cpp" "ML-KEM-1024 production"
check_file "security-lib/src/main/cpp/ml_dsa_87_production.cpp" "ML-DSA-87 production"
check_file "security-lib/src/main/cpp/ml_kem_1024.h" "ML-KEM-1024 header"
check_file "security-lib/src/main/cpp/ml_dsa_87.h" "ML-DSA-87 header"
echo ""

echo "=== 3. Test Implementations (Fallback) ==="
check_file "security-lib/src/main/cpp/ml_kem_1024.cpp" "ML-KEM-1024 test"
check_file "security-lib/src/main/cpp/ml_dsa_87.cpp" "ML-DSA-87 test"
echo ""

echo "=== 4. Supporting Crypto Files ==="
check_file "security-lib/src/main/cpp/el2_detector.cpp" "EL2 detector"
check_file "security-lib/src/main/cpp/performance_counters.cpp" "Performance counters"
check_file "security-lib/src/main/cpp/cache_operations.cpp" "Cache operations"
check_file "security-lib/src/main/cpp/memory_scrambler.cpp" "Memory scrambler"
check_file "security-lib/src/main/cpp/timing_obfuscation.cpp" "Timing obfuscation"
check_file "security-lib/src/main/cpp/jni_bridge.cpp" "JNI bridge"
echo ""

echo "=== 5. Kotlin API Wrappers ==="
check_file "security-lib/src/main/java/im/molly/security/MLKEM1024.kt" "ML-KEM-1024 Kotlin API"
check_file "security-lib/src/main/java/im/molly/security/MLDSA87.kt" "ML-DSA-87 Kotlin API"
check_file "security-lib/src/main/java/im/molly/security/EL2Detector.kt" "EL2 Detector Kotlin"
echo ""

echo "=== 6. Test Suite ==="
check_file "app/src/test/java/im/molly/app/security/SecurityTests.kt" "Unit tests"
check_file "app/src/androidTest/java/im/molly/app/benchmarks/CryptoBenchmarks.kt" "Benchmark tests"
echo ""

echo "=== 7. CMakeLists.txt Configuration ==="
check_in_file "security-lib/src/main/cpp/CMakeLists.txt" "PRODUCTION_CRYPTO" "PRODUCTION_CRYPTO option"
check_in_file "security-lib/src/main/cpp/CMakeLists.txt" "FetchLibOQS" "FetchLibOQS include"
check_in_file "security-lib/src/main/cpp/CMakeLists.txt" "ml_kem_1024_production.cpp" "Production ML-KEM source"
check_in_file "security-lib/src/main/cpp/CMakeLists.txt" "ml_dsa_87_production.cpp" "Production ML-DSA source"
check_in_file "security-lib/src/main/cpp/CMakeLists.txt" "target_link_libraries.*oqs" "liboqs linking"
echo ""

echo "=== 8. build.gradle.kts Configuration ==="
check_in_file "security-lib/build.gradle.kts" "ndkVersion" "NDK version specified"
check_in_file "security-lib/build.gradle.kts" "PRODUCTION_CRYPTO=ON" "Production flag"
check_in_file "security-lib/build.gradle.kts" "OQS_MINIMAL_BUILD=ON" "Minimal liboqs build"
check_in_file "security-lib/build.gradle.kts" "OQS_ENABLE_KEM_ml_kem_1024=ON" "ML-KEM enabled"
check_in_file "security-lib/build.gradle.kts" "OQS_ENABLE_SIG_ml_dsa_87=ON" "ML-DSA enabled"
check_in_file "security-lib/build.gradle.kts" "arm64-v8a" "ARM64 ABI filter"
echo ""

echo "=== 9. FetchLibOQS.cmake Configuration ==="
check_in_file "security-lib/cmake/FetchLibOQS.cmake" "FetchContent_Declare" "FetchContent declared"
check_in_file "security-lib/cmake/FetchLibOQS.cmake" "liboqs" "liboqs repository"
check_in_file "security-lib/cmake/FetchLibOQS.cmake" "0.11.0" "liboqs version 0.11.0"
check_in_file "security-lib/cmake/FetchLibOQS.cmake" "OQS_MINIMAL_BUILD" "Minimal build config"
echo ""

echo "=== 10. Production Code Validation ==="
if grep -q "OQS_KEM_ml_kem_1024_keypair" "security-lib/src/main/cpp/ml_kem_1024_production.cpp" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} ML-KEM-1024 uses liboqs API"
else
    echo -e "${RED}✗${NC} ML-KEM-1024 missing liboqs API calls"
    ((ERRORS++))
fi

if grep -q "OQS_SIG_ml_dsa_87_keypair" "security-lib/src/main/cpp/ml_dsa_87_production.cpp" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} ML-DSA-87 uses liboqs API"
else
    echo -e "${RED}✗${NC} ML-DSA-87 missing liboqs API calls"
    ((ERRORS++))
fi

if grep -q "#ifdef PRODUCTION_CRYPTO" "security-lib/src/main/cpp/ml_kem_1024_production.cpp" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} ML-KEM-1024 has conditional compilation"
else
    echo -e "${YELLOW}⚠${NC} ML-KEM-1024 missing conditional compilation guard"
    ((WARNINGS++))
fi

if grep -q "#ifdef PRODUCTION_CRYPTO" "security-lib/src/main/cpp/ml_dsa_87_production.cpp" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} ML-DSA-87 has conditional compilation"
else
    echo -e "${YELLOW}⚠${NC} ML-DSA-87 missing conditional compilation guard"
    ((WARNINGS++))
fi
echo ""

echo "=== 11. Documentation ==="
check_file "BUILD_GUIDE.md" "Build guide"
check_file "CRYPTO_UPGRADE.md" "Crypto upgrade doc"
check_file "docs/PRODUCTION_HARDENING_PLAN.md" "Production hardening plan"
check_file "docs/SECURITY_AUDIT_CHECKLIST.md" "Security audit checklist"
echo ""

echo "=== 12. Source Code Line Counts ==="
if [ -f "security-lib/src/main/cpp/ml_kem_1024_production.cpp" ]; then
    lines=$(wc -l < "security-lib/src/main/cpp/ml_kem_1024_production.cpp")
    echo -e "${BLUE}ℹ${NC} ML-KEM-1024 production: $lines lines"
fi

if [ -f "security-lib/src/main/cpp/ml_dsa_87_production.cpp" ]; then
    lines=$(wc -l < "security-lib/src/main/cpp/ml_dsa_87_production.cpp")
    echo -e "${BLUE}ℹ${NC} ML-DSA-87 production: $lines lines"
fi

if [ -f "app/src/androidTest/java/im/molly/app/benchmarks/CryptoBenchmarks.kt" ]; then
    lines=$(wc -l < "app/src/androidTest/java/im/molly/app/benchmarks/CryptoBenchmarks.kt")
    echo -e "${BLUE}ℹ${NC} Benchmark suite: $lines lines"
fi
echo ""

echo "=== 13. Syntax Validation ==="
# Check for common C++ syntax errors
if grep -E "(;\s*;|^\s*}\s*\{)" security-lib/src/main/cpp/*.cpp 2>/dev/null; then
    echo -e "${YELLOW}⚠${NC} Potential syntax issues in C++ files"
    ((WARNINGS++))
else
    echo -e "${GREEN}✓${NC} No obvious C++ syntax errors"
fi

# Check for missing includes
for file in security-lib/src/main/cpp/ml_*_production.cpp; do
    if [ -f "$file" ]; then
        if ! grep -q "#include.*oqs.h" "$file"; then
            echo -e "${YELLOW}⚠${NC} $(basename $file) may be missing oqs.h include"
            ((WARNINGS++))
        fi
    fi
done
echo ""

echo "=== 14. Gradle Wrapper ==="
check_file "gradlew" "Gradle wrapper script"
if [ -x "gradlew" ]; then
    echo -e "${GREEN}✓${NC} Gradle wrapper is executable"
else
    echo -e "${YELLOW}⚠${NC} Gradle wrapper is not executable (run: chmod +x gradlew)"
    ((WARNINGS++))
fi
echo ""

echo "=== 15. Expected Build Artifacts ==="
echo -e "${BLUE}ℹ${NC} When compilation succeeds, expect:"
echo "    - security-lib/build/intermediates/cmake/debug/obj/arm64-v8a/libmolly_security.so (~700 KB)"
echo "    - security-lib/build/intermediates/cmake/debug/obj/arm64-v8a/liboqs.a (~500 KB)"
echo "    - app/build/outputs/apk/debug/app-debug.apk (with embedded native lib)"
echo ""

echo "=== 16. Network Requirements ==="
echo -e "${BLUE}ℹ${NC} First build requires network access for:"
echo "    - Gradle dependencies download"
echo "    - liboqs v0.11.0 from github.com/open-quantum-safe/liboqs.git"
echo "    - OpenSSL (should be available on Android)"
echo ""

echo "========================================"
echo "COMPILATION READINESS SUMMARY"
echo "========================================"
echo ""

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ ALL CHECKS PASSED${NC}"
    echo ""
    echo "Build system is ready for compilation!"
    echo ""
    echo "To compile (requires network):"
    echo "  ./gradlew :security-lib:assembleDebug"
    echo ""
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ PASSED WITH $WARNINGS WARNING(S)${NC}"
    echo ""
    echo "Build system is ready but has minor issues."
    echo "Warnings are non-critical and compilation should succeed."
    echo ""
    echo "To compile (requires network):"
    echo "  ./gradlew :security-lib:assembleDebug"
    echo ""
    exit 0
else
    echo -e "${RED}✗ FAILED: $ERRORS ERROR(S), $WARNINGS WARNING(S)${NC}"
    echo ""
    echo "Build system has critical issues that must be fixed."
    echo "Compilation will likely fail."
    echo ""
    exit 1
fi
