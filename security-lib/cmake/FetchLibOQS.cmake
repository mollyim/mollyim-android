# FetchLibOQS.cmake
# Downloads and builds liboqs for Android

include(FetchContent)

message(STATUS "Fetching liboqs from GitHub...")

FetchContent_Declare(
    liboqs
    GIT_REPOSITORY https://github.com/open-quantum-safe/liboqs.git
    GIT_TAG 0.11.0
    GIT_SHALLOW TRUE
)

# Configure liboqs for minimal build (only ML-KEM-1024 and ML-DSA-87)
set(OQS_USE_OPENSSL ON CACHE BOOL "Use OpenSSL for AES and SHA" FORCE)
set(OQS_MINIMAL_BUILD ON CACHE BOOL "Build only required algorithms" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static library" FORCE)

# Enable only required algorithms
set(OQS_ENABLE_KEM_ml_kem_1024 ON CACHE BOOL "Enable ML-KEM-1024" FORCE)
set(OQS_ENABLE_SIG_ml_dsa_87 ON CACHE BOOL "Enable ML-DSA-87" FORCE)

# Disable all other algorithms
set(OQS_ENABLE_KEM_kyber_512 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_KEM_kyber_768 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_KEM_frodokem_640_aes OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_KEM_frodokem_976_aes OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_dilithium_2 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_dilithium_3 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_dilithium_5 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_falcon_512 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_falcon_1024 OFF CACHE BOOL "" FORCE)
set(OQS_ENABLE_SIG_sphincs_sha2_128f_simple OFF CACHE BOOL "" FORCE)

# ARM64 optimizations
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    set(OQS_SPEED_USE_ARM_NEON ON CACHE BOOL "Use ARM NEON" FORCE)
    message(STATUS "Enabling ARM64 NEON optimizations for liboqs")
endif()

# Compiler optimizations
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math -march=armv8-a+crypto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -march=armv8-a+crypto")

FetchContent_MakeAvailable(liboqs)

message(STATUS "liboqs configuration complete")
message(STATUS "  - ML-KEM-1024: ENABLED")
message(STATUS "  - ML-DSA-87: ENABLED")
message(STATUS "  - OpenSSL: ENABLED")
message(STATUS "  - ARM NEON: ${OQS_SPEED_USE_ARM_NEON}")
