#ifndef MOLLY_SECURITY_CACHE_OPERATIONS_H
#define MOLLY_SECURITY_CACHE_OPERATIONS_H

#include <cstdint>
#include <cstddef>

namespace molly {
namespace security {

class CacheOperations {
public:
    // Poison cache with dummy data to disrupt side-channel attacks
    static void poison_cache(int intensity_percent);

    // Flush cache lines
    static void flush_cache_range(void* addr, size_t size);

    // Prefetch data into cache (for obfuscation)
    static void prefetch_cache_range(void* addr, size_t size);

    // Fill cache with noise pattern
    static void fill_cache_with_noise(size_t size_kb);

private:
    static void flush_cache_line(void* addr);
    static void prefetch_cache_line(void* addr);
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_CACHE_OPERATIONS_H
