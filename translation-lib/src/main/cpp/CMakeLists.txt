cmake_minimum_required(VERSION 3.22.1)
project(molly_translation)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra")

add_library(molly_translation SHARED
    translation_engine.cpp
    model_loader.cpp
    int8_quantizer.cpp
    jni_bridge_translation.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(molly_translation
    ${log-lib}
    ${android-lib}
)
