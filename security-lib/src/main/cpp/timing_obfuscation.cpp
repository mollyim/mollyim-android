#include "timing_obfuscation.h"
#include <chrono>
#include <thread>
#include <random>
#include <android/log.h>

#define TAG "TimingObfuscation"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace molly {
namespace security {

void TimingObfuscation::random_delay_us(int min_us, int max_us) {
    if (max_us <= min_us) return;

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(min_us, max_us);

    int delay = dis(gen);
    std::this_thread::sleep_for(std::chrono::microseconds(delay));
}

void TimingObfuscation::exponential_delay_us(int mean_us) {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::exponential_distribution<> dis(1.0 / mean_us);

    int delay = static_cast<int>(dis(gen));
    std::this_thread::sleep_for(std::chrono::microseconds(delay));
}

void TimingObfuscation::busy_wait_us(int microseconds) {
    auto start = std::chrono::high_resolution_clock::now();
    auto target = start + std::chrono::microseconds(microseconds);

    volatile int dummy = 0;
    while (std::chrono::high_resolution_clock::now() < target) {
        dummy++;
    }
}

void TimingObfuscation::execute_with_obfuscation(std::function<void()> func, int chaos_percent) {
    if (chaos_percent <= 0) {
        func();
        return;
    }

    // Add pre-execution delay
    int pre_delay = (chaos_percent * 100);  // up to 10ms at 100%
    random_delay_us(0, pre_delay);

    // Execute function
    func();

    // Add post-execution delay
    int post_delay = (chaos_percent * 150);  // up to 15ms at 100%
    random_delay_us(0, post_delay);

    // Add timing noise
    add_timing_noise(chaos_percent / 2);
}

void TimingObfuscation::add_timing_noise(int intensity_percent) {
    if (intensity_percent <= 0) return;

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 100);

    // Random decision to add noise
    if (dis(gen) > intensity_percent) {
        return;
    }

    // Add variable busy-wait
    int noise_duration = (intensity_percent * 50);  // up to 5ms at 100%
    std::uniform_int_distribution<> duration_dis(0, noise_duration);

    busy_wait_us(duration_dis(gen));
}

void TimingObfuscation::jitter_sleep_ms(int base_ms, int jitter_percent) {
    if (base_ms <= 0) return;

    int jitter = (base_ms * jitter_percent) / 100;

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(-jitter, jitter);

    int actual_sleep = base_ms + dis(gen);
    if (actual_sleep < 0) actual_sleep = 0;

    std::this_thread::sleep_for(std::chrono::milliseconds(actual_sleep));
}

} // namespace security
} // namespace molly
