#include "memory_scrambler.h"
#include <cstring>
#include <random>
#include <vector>
#include <android/log.h>

#define TAG "MemoryScrambler"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace molly {
namespace security {

void MemoryScrambler::secure_wipe(void* addr, size_t size) {
    if (!addr || size == 0) return;

    volatile uint8_t* p = static_cast<volatile uint8_t*>(addr);

    // Multiple pass wipe (DoD 5220.22-M standard)
    // Pass 1: Write zeros
    for (size_t i = 0; i < size; i++) {
        p[i] = 0x00;
    }

    // Pass 2: Write ones
    for (size_t i = 0; i < size; i++) {
        p[i] = 0xFF;
    }

    // Pass 3: Write random
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);

    for (size_t i = 0; i < size; i++) {
        p[i] = dis(gen);
    }

    // Final pass: Write zeros
    for (size_t i = 0; i < size; i++) {
        p[i] = 0x00;
    }

    // Memory barrier to prevent optimization
    asm volatile("" ::: "memory");
}

void MemoryScrambler::scramble_memory(void* addr, size_t size) {
    if (!addr || size == 0) return;

    volatile uint8_t* p = static_cast<volatile uint8_t*>(addr);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);

    for (size_t i = 0; i < size; i++) {
        p[i] = dis(gen);
    }

    LOGD("Scrambled %zu bytes of memory", size);
}

void MemoryScrambler::fill_available_ram(int fill_percent) {
    if (fill_percent <= 0 || fill_percent > 100) {
        return;
    }

    LOGD("Attempting to fill %d%% of available RAM", fill_percent);

    const size_t CHUNK_SIZE = 10 * 1024 * 1024;  // 10MB chunks
    std::vector<uint8_t*> allocated_chunks;

    try {
        // Allocate until we fail
        size_t total_allocated = 0;
        while (true) {
            uint8_t* chunk = new uint8_t[CHUNK_SIZE];
            if (!chunk) break;

            // Fill with random data
            std::random_device rd;
            std::mt19937 gen(rd());
            std::uniform_int_distribution<> dis(0, 255);

            for (size_t i = 0; i < CHUNK_SIZE; i += 4096) {
                chunk[i] = dis(gen);
            }

            allocated_chunks.push_back(chunk);
            total_allocated += CHUNK_SIZE;

            // Check if we've allocated enough based on percentage
            if (allocated_chunks.size() >= static_cast<size_t>(fill_percent)) {
                break;
            }
        }

        LOGD("Allocated %zu MB, wiping and releasing", total_allocated / (1024 * 1024));

        // Wipe and free
        for (uint8_t* chunk : allocated_chunks) {
            secure_wipe(chunk, CHUNK_SIZE);
            delete[] chunk;
        }

    } catch (...) {
        LOGD("RAM fill completed, cleaning up");
        for (uint8_t* chunk : allocated_chunks) {
            delete[] chunk;
        }
    }
}

void MemoryScrambler::create_decoy_patterns(size_t size_mb) {
    size_t size_bytes = size_mb * 1024 * 1024;

    uint8_t* decoy_buffer = new uint8_t[size_bytes];

    // Create patterns that look like sensitive data
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);

    // Pattern 1: Fake key material (high entropy)
    for (size_t i = 0; i < size_bytes / 4; i++) {
        decoy_buffer[i] = dis(gen);
    }

    // Pattern 2: Fake text data (printable ASCII)
    std::uniform_int_distribution<> text_dis(32, 126);
    for (size_t i = size_bytes / 4; i < size_bytes / 2; i++) {
        decoy_buffer[i] = text_dis(gen);
    }

    // Pattern 3: Structured data (alternating patterns)
    for (size_t i = size_bytes / 2; i < size_bytes; i++) {
        decoy_buffer[i] = (i % 2 == 0) ? 0xAA : 0x55;
    }

    // Keep in memory briefly then scramble
    volatile uint8_t dummy = 0;
    for (size_t i = 0; i < size_bytes; i += 4096) {
        dummy += decoy_buffer[i];
    }

    scramble_memory(decoy_buffer, size_bytes);
    delete[] decoy_buffer;

    LOGD("Created and scrambled %zu MB of decoy patterns", size_mb);
}

void MemoryScrambler::overwrite_with_pattern(void* addr, size_t size, uint8_t pattern) {
    volatile uint8_t* p = static_cast<volatile uint8_t*>(addr);
    for (size_t i = 0; i < size; i++) {
        p[i] = pattern;
    }
}

} // namespace security
} // namespace molly
