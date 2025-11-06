cmake_minimum_required(VERSION 3.22.1)
project(molly_security)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fexceptions -frtti")

# Production crypto flag - set to ON for production builds with liboqs
option(PRODUCTION_CRYPTO "Use production liboqs crypto instead of test implementations" ON)

if(PRODUCTION_CRYPTO)
    message(STATUS "Building with PRODUCTION CRYPTO (liboqs)")

    # Include liboqs fetch and build configuration
    set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../../cmake ${CMAKE_MODULE_PATH})
    include(FetchLibOQS)

    # Add production crypto compile definition
    add_compile_definitions(PRODUCTION_CRYPTO)

    # Optimization flags for production
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -ffast-math")
    set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -ffast-math")

    # Use production implementations
    set(CRYPTO_SOURCES
        ml_kem_1024_production.cpp
        ml_dsa_87_production.cpp
    )
else()
    message(STATUS "Building with TEST CRYPTO (secure random)")

    # Use test implementations
    set(CRYPTO_SOURCES
        ml_kem_1024.cpp
        ml_dsa_87.cpp
    )
endif()

add_library(molly_security SHARED
    el2_detector.cpp
    performance_counters.cpp
    cache_operations.cpp
    memory_scrambler.cpp
    timing_obfuscation.cpp
    kyber1024.cpp
    ${CRYPTO_SOURCES}
    jni_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(crypto-lib crypto)

target_link_libraries(molly_security
    ${log-lib}
    ${android-lib}
    ${crypto-lib}
)

if(PRODUCTION_CRYPTO)
    # Link against liboqs for production crypto
    target_link_libraries(molly_security oqs)

    # Include liboqs headers
    target_include_directories(molly_security PRIVATE
        ${liboqs_SOURCE_DIR}/src
    )

    message(STATUS "Linked against liboqs for production crypto")
endif()
