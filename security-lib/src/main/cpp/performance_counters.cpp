#include "performance_counters.h"
#include <unistd.h>
#include <sys/syscall.h>
#include <sys/ioctl.h>
#include <cstring>
#include <android/log.h>

#define TAG "MollySecurity"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace molly {
namespace security {

static long perf_event_open(struct perf_event_attr *hw_event, pid_t pid,
                            int cpu, int group_fd, unsigned long flags) {
    return syscall(__NR_perf_event_open, hw_event, pid, cpu, group_fd, flags);
}

PerformanceCounters::PerformanceCounters() : initialized_(false) {
}

PerformanceCounters::~PerformanceCounters() {
    close_counters();
}

int PerformanceCounters::create_perf_counter(uint32_t type, uint64_t config) {
    struct perf_event_attr pe;
    memset(&pe, 0, sizeof(struct perf_event_attr));
    pe.type = type;
    pe.size = sizeof(struct perf_event_attr);
    pe.config = config;
    pe.disabled = 1;
    pe.exclude_kernel = 0;
    pe.exclude_hv = 0;  // Include hypervisor (if we can access it)

    int fd = perf_event_open(&pe, 0, -1, -1, 0);
    if (fd == -1) {
        LOGE("Failed to open perf counter type=%u config=%llu: %s",
             type, (unsigned long long)config, strerror(errno));
    }
    return fd;
}

bool PerformanceCounters::initialize() {
    if (initialized_) {
        return true;
    }

    LOGD("Initializing performance counters");

    // Try to create 8 different performance counters
    int fd;

    // CPU cycles
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_CPU_CYCLES);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Instructions
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_INSTRUCTIONS);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Cache references
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_CACHE_REFERENCES);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Cache misses
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_CACHE_MISSES);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Branch instructions
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_BRANCH_INSTRUCTIONS);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Branch misses
    fd = create_perf_counter(PERF_TYPE_HARDWARE, PERF_COUNT_HW_BRANCH_MISSES);
    if (fd >= 0) counter_fds_.push_back(fd);

    // Context switches
    fd = create_perf_counter(PERF_TYPE_SOFTWARE, PERF_COUNT_SW_CONTEXT_SWITCHES);
    if (fd >= 0) counter_fds_.push_back(fd);

    // CPU migrations
    fd = create_perf_counter(PERF_TYPE_SOFTWARE, PERF_COUNT_SW_CPU_MIGRATIONS);
    if (fd >= 0) counter_fds_.push_back(fd);

    initialized_ = !counter_fds_.empty();

    if (initialized_) {
        LOGD("Successfully initialized %zu performance counters", counter_fds_.size());

        // Enable all counters
        for (int counter_fd : counter_fds_) {
            ioctl(counter_fd, PERF_EVENT_IOC_RESET, 0);
            ioctl(counter_fd, PERF_EVENT_IOC_ENABLE, 0);
        }
    } else {
        LOGE("Failed to initialize any performance counters");
    }

    return initialized_;
}

bool PerformanceCounters::read_counters(PerfCounterData& data) {
    if (!initialized_ || counter_fds_.size() < 2) {
        return false;
    }

    memset(&data, 0, sizeof(PerfCounterData));

    size_t idx = 0;
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.cycles, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.instructions, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.cache_references, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.cache_misses, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.branch_instructions, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.branch_misses, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.context_switches, sizeof(uint64_t));
    }
    if (idx < counter_fds_.size()) {
        read(counter_fds_[idx++], &data.cpu_migrations, sizeof(uint64_t));
    }

    return true;
}

void PerformanceCounters::close_counters() {
    for (int fd : counter_fds_) {
        if (fd >= 0) {
            close(fd);
        }
    }
    counter_fds_.clear();
    initialized_ = false;
}

bool PerformanceCounters::are_counters_accessible() {
    return initialized_ && (counter_fds_.size() >= 6);
}

} // namespace security
} // namespace molly
