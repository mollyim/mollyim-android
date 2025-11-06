#ifndef MOLLY_SECURITY_MEMORY_SCRAMBLER_H
#define MOLLY_SECURITY_MEMORY_SCRAMBLER_H

#include <cstddef>
#include <cstdint>

namespace molly {
namespace security {

class MemoryScrambler {
public:
    // Securely wipe memory region
    static void secure_wipe(void* addr, size_t size);

    // Scramble memory with random data
    static void scramble_memory(void* addr, size_t size);

    // Fill all available RAM (for wiping sensitive data)
    static void fill_available_ram(int fill_percent);

    // Create decoy memory patterns
    static void create_decoy_patterns(size_t size_mb);

private:
    static void overwrite_with_pattern(void* addr, size_t size, uint8_t pattern);
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_MEMORY_SCRAMBLER_H
