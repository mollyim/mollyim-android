#include "cache_operations.h"
#include <cstring>
#include <random>
#include <android/log.h>

#define TAG "CacheOps"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace molly {
namespace security {

void CacheOperations::flush_cache_line(void* addr) {
#ifdef __aarch64__
    asm volatile("dc civac, %0" : : "r" (addr) : "memory");
#endif
}

void CacheOperations::prefetch_cache_line(void* addr) {
#ifdef __aarch64__
    asm volatile("prfm pldl1keep, [%0]" : : "r" (addr));
#endif
}

void CacheOperations::poison_cache(int intensity_percent) {
    // Allocate and access random memory locations to pollute cache
    size_t poison_size = (1024 * 1024 * intensity_percent) / 100;  // Up to 1MB
    if (poison_size == 0) return;

    uint8_t* poison_buffer = new uint8_t[poison_size];

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, poison_size - 64);

    // Randomly access cache lines
    int num_accesses = (intensity_percent * 100);
    for (int i = 0; i < num_accesses; i++) {
        size_t offset = dis(gen);
        volatile uint8_t dummy = poison_buffer[offset];
        (void)dummy;
    }

    delete[] poison_buffer;

    LOGD("Cache poisoned with intensity %d%%", intensity_percent);
}

void CacheOperations::flush_cache_range(void* addr, size_t size) {
    uint8_t* p = static_cast<uint8_t*>(addr);
    for (size_t i = 0; i < size; i += 64) {
        flush_cache_line(&p[i]);
    }
#ifdef __aarch64__
    asm volatile("dsb sy" ::: "memory");
#endif
}

void CacheOperations::prefetch_cache_range(void* addr, size_t size) {
    uint8_t* p = static_cast<uint8_t*>(addr);
    for (size_t i = 0; i < size; i += 64) {
        prefetch_cache_line(&p[i]);
    }
}

void CacheOperations::fill_cache_with_noise(size_t size_kb) {
    size_t size_bytes = size_kb * 1024;
    uint8_t* noise_buffer = new uint8_t[size_bytes];

    // Fill with pseudo-random data
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);

    for (size_t i = 0; i < size_bytes; i++) {
        noise_buffer[i] = dis(gen);
    }

    // Access all data to load into cache
    volatile uint8_t dummy = 0;
    for (size_t i = 0; i < size_bytes; i += 64) {
        dummy += noise_buffer[i];
    }

    delete[] noise_buffer;

    LOGD("Filled cache with %zu KB of noise", size_kb);
}

} // namespace security
} // namespace molly
