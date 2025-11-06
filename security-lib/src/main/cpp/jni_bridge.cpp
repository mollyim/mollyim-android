#include <jni.h>
#include <android/log.h>
#include <memory>
#include "el2_detector.h"
#include "cache_operations.h"
#include "memory_scrambler.h"
#include "timing_obfuscation.h"

#define TAG "MollySecurityJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace molly::security;

// Global detector instance
static std::unique_ptr<EL2Detector> g_detector;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_im_molly_security_EL2Detector_nativeInitialize(JNIEnv* env, jobject /* this */) {
    try {
        g_detector = std::make_unique<EL2Detector>();
        return g_detector->initialize() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        LOGE("Failed to initialize EL2 detector");
        return JNI_FALSE;
    }
}

JNIEXPORT jobject JNICALL
Java_im_molly_security_EL2Detector_nativeAnalyzeThreat(JNIEnv* env, jobject /* this */) {
    if (!g_detector) {
        LOGE("EL2 detector not initialized");
        return nullptr;
    }

    ThreatAnalysis analysis = g_detector->analyze_threat();

    // Create ThreatAnalysis Java object
    jclass threatClass = env->FindClass("im/molly/security/ThreatAnalysis");
    if (!threatClass) {
        LOGE("Could not find ThreatAnalysis class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(threatClass, "<init>",
        "(FFZZZZJ)V");
    if (!constructor) {
        LOGE("Could not find ThreatAnalysis constructor");
        return nullptr;
    }

    jobject result = env->NewObject(threatClass, constructor,
        analysis.threat_level,
        analysis.hypervisor_confidence,
        analysis.timing_anomaly_detected ? JNI_TRUE : JNI_FALSE,
        analysis.cache_anomaly_detected ? JNI_TRUE : JNI_FALSE,
        analysis.perf_counter_blocked ? JNI_TRUE : JNI_FALSE,
        analysis.memory_anomaly_detected ? JNI_TRUE : JNI_FALSE,
        (jlong)analysis.analysis_timestamp
    );

    return result;
}

JNIEXPORT void JNICALL
Java_im_molly_security_CacheOperations_nativePoisonCache(JNIEnv* env, jclass /* clazz */,
    jint intensity) {
    CacheOperations::poison_cache(intensity);
}

JNIEXPORT void JNICALL
Java_im_molly_security_CacheOperations_nativeFillCacheWithNoise(JNIEnv* env, jclass /* clazz */,
    jint sizeKb) {
    CacheOperations::fill_cache_with_noise(sizeKb);
}

JNIEXPORT void JNICALL
Java_im_molly_security_MemoryScrambler_nativeSecureWipe(JNIEnv* env, jclass /* clazz */,
    jbyteArray data) {
    if (!data) return;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    jsize length = env->GetArrayLength(data);

    if (bytes && length > 0) {
        MemoryScrambler::secure_wipe(bytes, length);
        env->ReleaseByteArrayElements(data, bytes, 0);
    }
}

JNIEXPORT void JNICALL
Java_im_molly_security_MemoryScrambler_nativeFillAvailableRAM(JNIEnv* env, jclass /* clazz */,
    jint fillPercent) {
    MemoryScrambler::fill_available_ram(fillPercent);
}

JNIEXPORT void JNICALL
Java_im_molly_security_MemoryScrambler_nativeCreateDecoyPatterns(JNIEnv* env, jclass /* clazz */,
    jint sizeMb) {
    MemoryScrambler::create_decoy_patterns(sizeMb);
}

JNIEXPORT void JNICALL
Java_im_molly_security_TimingObfuscation_nativeRandomDelay(JNIEnv* env, jclass /* clazz */,
    jint minUs, jint maxUs) {
    TimingObfuscation::random_delay_us(minUs, maxUs);
}

JNIEXPORT void JNICALL
Java_im_molly_security_TimingObfuscation_nativeAddTimingNoise(JNIEnv* env, jclass /* clazz */,
    jint intensity) {
    TimingObfuscation::add_timing_noise(intensity);
}

JNIEXPORT void JNICALL
Java_im_molly_security_TimingObfuscation_nativeJitterSleep(JNIEnv* env, jclass /* clazz */,
    jint baseMs, jint jitterPercent) {
    TimingObfuscation::jitter_sleep_ms(baseMs, jitterPercent);
}

} // extern "C"
