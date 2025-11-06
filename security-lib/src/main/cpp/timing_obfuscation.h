#ifndef MOLLY_SECURITY_TIMING_OBFUSCATION_H
#define MOLLY_SECURITY_TIMING_OBFUSCATION_H

#include <cstdint>
#include <functional>

namespace molly {
namespace security {

class TimingObfuscation {
public:
    // Add random delay to obfuscate timing
    static void random_delay_us(int min_us, int max_us);

    // Add delay with exponential distribution
    static void exponential_delay_us(int mean_us);

    // Execute function with timing obfuscation
    static void execute_with_obfuscation(std::function<void()> func, int chaos_percent);

    // Add busy-wait noise
    static void add_timing_noise(int intensity_percent);

    // Sleep with jitter
    static void jitter_sleep_ms(int base_ms, int jitter_percent);

private:
    static void busy_wait_us(int microseconds);
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_TIMING_OBFUSCATION_H
