#ifndef MOLLY_SECURITY_PERFORMANCE_COUNTERS_H
#define MOLLY_SECURITY_PERFORMANCE_COUNTERS_H

#include <cstdint>
#include <sys/types.h>
#include <linux/perf_event.h>
#include <vector>

namespace molly {
namespace security {

struct PerfCounterData {
    uint64_t cycles;
    uint64_t instructions;
    uint64_t cache_references;
    uint64_t cache_misses;
    uint64_t branch_instructions;
    uint64_t branch_misses;
    uint64_t context_switches;
    uint64_t cpu_migrations;
};

class PerformanceCounters {
public:
    PerformanceCounters();
    ~PerformanceCounters();

    bool initialize();
    bool read_counters(PerfCounterData& data);
    void close_counters();

    // Check if performance counters are available (may be blocked by hypervisor)
    bool are_counters_accessible();

private:
    int create_perf_counter(uint32_t type, uint64_t config);

    std::vector<int> counter_fds_;
    bool initialized_;
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_PERFORMANCE_COUNTERS_H
