#ifndef MOLLY_SECURITY_EL2_DETECTOR_H
#define MOLLY_SECURITY_EL2_DETECTOR_H

#include "performance_counters.h"
#include <cstdint>
#include <memory>

namespace molly {
namespace security {

struct ThreatAnalysis {
    float threat_level;           // 0.0 - 1.0
    float hypervisor_confidence;  // 0.0 - 1.0
    bool timing_anomaly_detected;
    bool cache_anomaly_detected;
    bool perf_counter_blocked;
    bool memory_anomaly_detected;
    uint64_t analysis_timestamp;
};

class EL2Detector {
public:
    EL2Detector();
    ~EL2Detector();

    bool initialize();
    ThreatAnalysis analyze_threat();

private:
    // Detection methods
    float detect_timing_anomalies();
    float detect_cache_anomalies();
    float detect_perf_counter_blocking();
    float detect_memory_anomalies();

    // Baseline measurements
    void establish_baseline();
    bool is_baseline_established();

    // Helper functions
    uint64_t rdtsc();  // Read timestamp counter
    void cache_flush(void* ptr, size_t size);
    void cache_probe(void* ptr, size_t size);

    std::unique_ptr<PerformanceCounters> perf_counters_;

    // Baseline data
    struct Baseline {
        uint64_t avg_cache_latency;
        uint64_t avg_instruction_latency;
        double avg_cycles_per_instruction;
        double avg_cache_miss_rate;
        bool established;
    } baseline_;

    // Detection state
    uint64_t last_analysis_time_;
    uint32_t consecutive_detections_;
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_EL2_DETECTOR_H
