#include "el2_detector.h"
#include <cstring>
#include <cmath>
#include <chrono>
#include <algorithm>
#include <android/log.h>

#define TAG "EL2Detector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace molly {
namespace security {

// Inline assembly for timestamp counter reading
#ifdef __aarch64__
static inline uint64_t read_timestamp() {
    uint64_t val;
    asm volatile("mrs %0, cntvct_el0" : "=r" (val));
    return val;
}
#else
static inline uint64_t read_timestamp() {
    return std::chrono::high_resolution_clock::now().time_since_epoch().count();
}
#endif

EL2Detector::EL2Detector() : last_analysis_time_(0), consecutive_detections_(0) {
    memset(&baseline_, 0, sizeof(Baseline));
}

EL2Detector::~EL2Detector() {
}

bool EL2Detector::initialize() {
    LOGD("Initializing EL2 Detector");

    perf_counters_ = std::make_unique<PerformanceCounters>();
    if (!perf_counters_->initialize()) {
        LOGW("Performance counters not available - limited detection capability");
    }

    establish_baseline();

    LOGD("EL2 Detector initialized successfully");
    return true;
}

void EL2Detector::establish_baseline() {
    LOGD("Establishing baseline measurements");

    const int NUM_SAMPLES = 100;
    uint64_t total_cache_latency = 0;
    uint64_t total_instruction_latency = 0;
    std::vector<double> cpi_samples;
    std::vector<double> miss_rate_samples;

    // Allocate test buffer for cache measurements
    const size_t BUFFER_SIZE = 256 * 1024;  // 256KB
    uint8_t* test_buffer = new uint8_t[BUFFER_SIZE];
    memset(test_buffer, 0xAA, BUFFER_SIZE);

    for (int i = 0; i < NUM_SAMPLES; i++) {
        // Measure cache latency
        cache_flush(test_buffer, BUFFER_SIZE);
        uint64_t start = read_timestamp();
        cache_probe(test_buffer, BUFFER_SIZE);
        uint64_t end = read_timestamp();
        total_cache_latency += (end - start);

        // Measure instruction latency
        start = read_timestamp();
        volatile int dummy = 0;
        for (int j = 0; j < 1000; j++) {
            dummy += j;
        }
        end = read_timestamp();
        total_instruction_latency += (end - start);

        // Measure CPI and cache miss rate if counters available
        if (perf_counters_ && perf_counters_->are_counters_accessible()) {
            PerfCounterData data;
            if (perf_counters_->read_counters(data)) {
                if (data.instructions > 0) {
                    double cpi = static_cast<double>(data.cycles) / data.instructions;
                    cpi_samples.push_back(cpi);
                }
                if (data.cache_references > 0) {
                    double miss_rate = static_cast<double>(data.cache_misses) / data.cache_references;
                    miss_rate_samples.push_back(miss_rate);
                }
            }
        }
    }

    delete[] test_buffer;

    baseline_.avg_cache_latency = total_cache_latency / NUM_SAMPLES;
    baseline_.avg_instruction_latency = total_instruction_latency / NUM_SAMPLES;

    if (!cpi_samples.empty()) {
        double sum = 0;
        for (double cpi : cpi_samples) sum += cpi;
        baseline_.avg_cycles_per_instruction = sum / cpi_samples.size();
    } else {
        baseline_.avg_cycles_per_instruction = 1.5;  // Reasonable default
    }

    if (!miss_rate_samples.empty()) {
        double sum = 0;
        for (double mr : miss_rate_samples) sum += mr;
        baseline_.avg_cache_miss_rate = sum / miss_rate_samples.size();
    } else {
        baseline_.avg_cache_miss_rate = 0.05;  // 5% default
    }

    baseline_.established = true;

    LOGD("Baseline established - cache_latency=%llu, instr_latency=%llu, cpi=%.2f, miss_rate=%.4f",
         (unsigned long long)baseline_.avg_cache_latency,
         (unsigned long long)baseline_.avg_instruction_latency,
         baseline_.avg_cycles_per_instruction,
         baseline_.avg_cache_miss_rate);
}

bool EL2Detector::is_baseline_established() {
    return baseline_.established;
}

float EL2Detector::detect_timing_anomalies() {
    if (!is_baseline_established()) {
        return 0.0f;
    }

    const int NUM_TESTS = 10;
    int anomalies_detected = 0;

    for (int i = 0; i < NUM_TESTS; i++) {
        // Test instruction timing
        uint64_t start = read_timestamp();
        volatile int dummy = 0;
        for (int j = 0; j < 1000; j++) {
            dummy += j;
        }
        uint64_t end = read_timestamp();
        uint64_t latency = end - start;

        // Check if latency is significantly higher than baseline
        // Hypervisor context switches add ~10-50us overhead
        float deviation = static_cast<float>(latency) / baseline_.avg_instruction_latency;

        if (deviation > 2.5f) {  // More than 2.5x baseline
            anomalies_detected++;
        }
    }

    float confidence = static_cast<float>(anomalies_detected) / NUM_TESTS;
    if (confidence > 0.3f) {
        LOGW("Timing anomalies detected: %.1f%% of tests", confidence * 100.0f);
    }

    return confidence;
}

float EL2Detector::detect_cache_anomalies() {
    if (!is_baseline_established()) {
        return 0.0f;
    }

    const int NUM_TESTS = 10;
    const size_t BUFFER_SIZE = 128 * 1024;
    uint8_t* test_buffer = new uint8_t[BUFFER_SIZE];
    memset(test_buffer, 0xBB, BUFFER_SIZE);

    int anomalies_detected = 0;

    for (int i = 0; i < NUM_TESTS; i++) {
        cache_flush(test_buffer, BUFFER_SIZE);

        uint64_t start = read_timestamp();
        cache_probe(test_buffer, BUFFER_SIZE);
        uint64_t end = read_timestamp();

        uint64_t latency = end - start;
        float deviation = static_cast<float>(latency) / baseline_.avg_cache_latency;

        // Hypervisor may cause cache pollution or altered cache behavior
        if (deviation > 2.0f) {
            anomalies_detected++;
        }
    }

    delete[] test_buffer;

    float confidence = static_cast<float>(anomalies_detected) / NUM_TESTS;
    if (confidence > 0.3f) {
        LOGW("Cache anomalies detected: %.1f%% of tests", confidence * 100.0f);
    }

    return confidence;
}

float EL2Detector::detect_perf_counter_blocking() {
    if (!perf_counters_) {
        return 0.5f;  // No counters available is suspicious
    }

    // Check if we can access performance counters
    // Hypervisors often block or restrict access to hardware counters
    if (!perf_counters_->are_counters_accessible()) {
        LOGW("Performance counters are blocked or restricted");
        return 0.9f;  // High confidence of hypervisor
    }

    // Check if counter values make sense
    PerfCounterData data;
    if (perf_counters_->read_counters(data)) {
        // Sanity check: CPI should be reasonable (0.5 - 4.0 typically)
        if (data.instructions > 1000000) {
            double cpi = static_cast<double>(data.cycles) / data.instructions;

            if (cpi < 0.1 || cpi > 10.0) {
                LOGW("Suspicious CPI value: %.2f", cpi);
                return 0.6f;
            }

            // Check against baseline
            float deviation = std::abs(cpi - baseline_.avg_cycles_per_instruction) /
                            baseline_.avg_cycles_per_instruction;

            if (deviation > 0.5f) {  // 50% deviation
                return 0.5f;
            }
        }
    }

    return 0.0f;
}

float EL2Detector::detect_memory_anomalies() {
    // Test for memory access patterns that indicate virtualization
    // This is a simplified implementation
    const size_t PAGE_SIZE = 4096;
    const int NUM_PAGES = 100;

    uint8_t** pages = new uint8_t*[NUM_PAGES];
    for (int i = 0; i < NUM_PAGES; i++) {
        pages[i] = new uint8_t[PAGE_SIZE];
        memset(pages[i], i & 0xFF, PAGE_SIZE);
    }

    // Measure access latency to different pages
    uint64_t total_latency = 0;
    uint64_t max_latency = 0;
    int page_faults = 0;

    for (int i = 0; i < NUM_PAGES; i++) {
        uint64_t start = read_timestamp();
        volatile uint8_t dummy = pages[i][0];
        uint64_t end = read_timestamp();

        uint64_t latency = end - start;
        total_latency += latency;

        if (latency > max_latency) {
            max_latency = latency;
        }

        // Detect unusually long accesses (possible VM exits)
        if (latency > baseline_.avg_cache_latency * 10) {
            page_faults++;
        }
    }

    for (int i = 0; i < NUM_PAGES; i++) {
        delete[] pages[i];
    }
    delete[] pages;

    // High variance in memory access times suggests virtualization
    uint64_t avg_latency = total_latency / NUM_PAGES;
    float variance_ratio = static_cast<float>(max_latency) / avg_latency;

    float confidence = 0.0f;
    if (variance_ratio > 20.0f) {
        confidence = 0.4f;
    }
    if (page_faults > NUM_PAGES / 10) {
        confidence += 0.3f;
    }

    return std::min(confidence, 1.0f);
}

uint64_t EL2Detector::rdtsc() {
    return read_timestamp();
}

void EL2Detector::cache_flush(void* ptr, size_t size) {
#ifdef __aarch64__
    uint8_t* p = static_cast<uint8_t*>(ptr);
    for (size_t i = 0; i < size; i += 64) {  // Cache line size
        asm volatile("dc civac, %0" : : "r" (&p[i]) : "memory");
    }
    asm volatile("dsb sy" ::: "memory");
#else
    // Fallback for non-ARM
    volatile uint8_t dummy;
    (void)dummy;
#endif
}

void EL2Detector::cache_probe(void* ptr, size_t size) {
    volatile uint8_t* p = static_cast<volatile uint8_t*>(ptr);
    volatile uint8_t dummy = 0;
    for (size_t i = 0; i < size; i += 64) {
        dummy += p[i];
    }
}

ThreatAnalysis EL2Detector::analyze_threat() {
    ThreatAnalysis result;
    memset(&result, 0, sizeof(ThreatAnalysis));

    uint64_t now = read_timestamp();
    result.analysis_timestamp = now;
    last_analysis_time_ = now;

    if (!is_baseline_established()) {
        establish_baseline();
    }

    LOGD("Starting threat analysis");

    // Run all detection methods
    float timing_score = detect_timing_anomalies();
    float cache_score = detect_cache_anomalies();
    float perf_counter_score = detect_perf_counter_blocking();
    float memory_score = detect_memory_anomalies();

    result.timing_anomaly_detected = (timing_score > 0.4f);
    result.cache_anomaly_detected = (cache_score > 0.4f);
    result.perf_counter_blocked = (perf_counter_score > 0.7f);
    result.memory_anomaly_detected = (memory_score > 0.4f);

    // Calculate overall hypervisor confidence (weighted average)
    result.hypervisor_confidence =
        timing_score * 0.30f +
        cache_score * 0.25f +
        perf_counter_score * 0.30f +
        memory_score * 0.15f;

    // Calculate threat level (adjusted for severity)
    result.threat_level = result.hypervisor_confidence;

    // Track consecutive detections
    if (result.hypervisor_confidence > 0.5f) {
        consecutive_detections_++;
    } else {
        consecutive_detections_ = 0;
    }

    // Boost threat level if consistently detected
    if (consecutive_detections_ >= 3) {
        result.threat_level = std::min(result.threat_level * 1.2f, 1.0f);
    }

    LOGD("Threat analysis complete - threat_level=%.2f, hypervisor_confidence=%.2f",
         result.threat_level, result.hypervisor_confidence);
    LOGD("  timing=%.2f, cache=%.2f, perf=%.2f, memory=%.2f",
         timing_score, cache_score, perf_counter_score, memory_score);

    return result;
}

} // namespace security
} // namespace molly
