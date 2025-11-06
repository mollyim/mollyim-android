cmake_minimum_required(VERSION 3.22.1)
project(molly_security)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fexceptions -frtti")

add_library(molly_security SHARED
    el2_detector.cpp
    performance_counters.cpp
    cache_operations.cpp
    memory_scrambler.cpp
    timing_obfuscation.cpp
    kyber1024.cpp
    ml_kem_1024.cpp
    ml_dsa_87.cpp
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
