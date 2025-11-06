#include <jni.h>
#include <android/log.h>
#include <memory>
#include "el2_detector.h"
#include "cache_operations.h"
#include "memory_scrambler.h"
#include "timing_obfuscation.h"
#include "ml_kem_1024.h"
#include "ml_dsa_87.h"

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

// ============================================================================
// ML-KEM-1024 (FIPS 203) JNI Bindings
// ============================================================================

JNIEXPORT jobject JNICALL
Java_im_molly_security_MLKEM1024_nativeGenerateKeypair(JNIEnv* env, jclass /* clazz */) {
    try {
        auto keypair = MLKEM1024::generate_keypair();

        // Create byte arrays
        jbyteArray publicKey = env->NewByteArray(keypair.public_key.size());
        jbyteArray secretKey = env->NewByteArray(keypair.secret_key.size());

        env->SetByteArrayRegion(publicKey, 0, keypair.public_key.size(),
                                reinterpret_cast<const jbyte*>(keypair.public_key.data()));
        env->SetByteArrayRegion(secretKey, 0, keypair.secret_key.size(),
                                reinterpret_cast<const jbyte*>(keypair.secret_key.data()));

        // Find KeyPair class and constructor
        jclass keypairClass = env->FindClass("im/molly/security/MLKEM1024$KeyPair");
        if (!keypairClass) {
            LOGE("Could not find MLKEM1024$KeyPair class");
            return nullptr;
        }

        jmethodID constructor = env->GetMethodID(keypairClass, "<init>", "([B[B)V");
        if (!constructor) {
            LOGE("Could not find MLKEM1024$KeyPair constructor");
            return nullptr;
        }

        return env->NewObject(keypairClass, constructor, publicKey, secretKey);
    } catch (const std::exception& e) {
        LOGE("ML-KEM-1024 keypair generation failed: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL
Java_im_molly_security_MLKEM1024_nativeEncapsulate(JNIEnv* env, jclass /* clazz */,
    jbyteArray publicKey) {
    if (!publicKey) return nullptr;

    try {
        jbyte* pkBytes = env->GetByteArrayElements(publicKey, nullptr);
        jsize pkLength = env->GetArrayLength(publicKey);

        std::vector<uint8_t> pk(reinterpret_cast<uint8_t*>(pkBytes),
                                reinterpret_cast<uint8_t*>(pkBytes) + pkLength);
        env->ReleaseByteArrayElements(publicKey, pkBytes, JNI_ABORT);

        auto result = MLKEM1024::encapsulate(pk);

        // Create byte arrays
        jbyteArray ciphertext = env->NewByteArray(result.ciphertext.size());
        jbyteArray sharedSecret = env->NewByteArray(result.shared_secret.size());

        env->SetByteArrayRegion(ciphertext, 0, result.ciphertext.size(),
                                reinterpret_cast<const jbyte*>(result.ciphertext.data()));
        env->SetByteArrayRegion(sharedSecret, 0, result.shared_secret.size(),
                                reinterpret_cast<const jbyte*>(result.shared_secret.data()));

        // Find EncapsulationResult class and constructor
        jclass resultClass = env->FindClass("im/molly/security/MLKEM1024$EncapsulationResult");
        if (!resultClass) {
            LOGE("Could not find MLKEM1024$EncapsulationResult class");
            return nullptr;
        }

        jmethodID constructor = env->GetMethodID(resultClass, "<init>", "([B[B)V");
        if (!constructor) {
            LOGE("Could not find MLKEM1024$EncapsulationResult constructor");
            return nullptr;
        }

        return env->NewObject(resultClass, constructor, ciphertext, sharedSecret);
    } catch (const std::exception& e) {
        LOGE("ML-KEM-1024 encapsulation failed: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_im_molly_security_MLKEM1024_nativeDecapsulate(JNIEnv* env, jclass /* clazz */,
    jbyteArray ciphertext, jbyteArray secretKey) {
    if (!ciphertext || !secretKey) return nullptr;

    try {
        jbyte* ctBytes = env->GetByteArrayElements(ciphertext, nullptr);
        jsize ctLength = env->GetArrayLength(ciphertext);
        jbyte* skBytes = env->GetByteArrayElements(secretKey, nullptr);
        jsize skLength = env->GetArrayLength(secretKey);

        std::vector<uint8_t> ct(reinterpret_cast<uint8_t*>(ctBytes),
                                reinterpret_cast<uint8_t*>(ctBytes) + ctLength);
        std::vector<uint8_t> sk(reinterpret_cast<uint8_t*>(skBytes),
                                reinterpret_cast<uint8_t*>(skBytes) + skLength);

        env->ReleaseByteArrayElements(ciphertext, ctBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(secretKey, skBytes, JNI_ABORT);

        auto sharedSecret = MLKEM1024::decapsulate(ct, sk);

        jbyteArray result = env->NewByteArray(sharedSecret.size());
        env->SetByteArrayRegion(result, 0, sharedSecret.size(),
                                reinterpret_cast<const jbyte*>(sharedSecret.data()));

        return result;
    } catch (const std::exception& e) {
        LOGE("ML-KEM-1024 decapsulation failed: %s", e.what());
        return nullptr;
    }
}

// ============================================================================
// ML-DSA-87 (FIPS 204) JNI Bindings
// ============================================================================

JNIEXPORT jobject JNICALL
Java_im_molly_security_MLDSA87_nativeGenerateKeypair(JNIEnv* env, jclass /* clazz */) {
    try {
        auto keypair = MLDSA87::generate_keypair();

        // Create byte arrays
        jbyteArray publicKey = env->NewByteArray(keypair.public_key.size());
        jbyteArray secretKey = env->NewByteArray(keypair.secret_key.size());

        env->SetByteArrayRegion(publicKey, 0, keypair.public_key.size(),
                                reinterpret_cast<const jbyte*>(keypair.public_key.data()));
        env->SetByteArrayRegion(secretKey, 0, keypair.secret_key.size(),
                                reinterpret_cast<const jbyte*>(keypair.secret_key.data()));

        // Find KeyPair class and constructor
        jclass keypairClass = env->FindClass("im/molly/security/MLDSA87$KeyPair");
        if (!keypairClass) {
            LOGE("Could not find MLDSA87$KeyPair class");
            return nullptr;
        }

        jmethodID constructor = env->GetMethodID(keypairClass, "<init>", "([B[B)V");
        if (!constructor) {
            LOGE("Could not find MLDSA87$KeyPair constructor");
            return nullptr;
        }

        return env->NewObject(keypairClass, constructor, publicKey, secretKey);
    } catch (const std::exception& e) {
        LOGE("ML-DSA-87 keypair generation failed: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_im_molly_security_MLDSA87_nativeSign(JNIEnv* env, jclass /* clazz */,
    jbyteArray message, jbyteArray secretKey) {
    if (!message || !secretKey) return nullptr;

    try {
        jbyte* msgBytes = env->GetByteArrayElements(message, nullptr);
        jsize msgLength = env->GetArrayLength(message);
        jbyte* skBytes = env->GetByteArrayElements(secretKey, nullptr);
        jsize skLength = env->GetArrayLength(secretKey);

        std::vector<uint8_t> msg(reinterpret_cast<uint8_t*>(msgBytes),
                                 reinterpret_cast<uint8_t*>(msgBytes) + msgLength);
        std::vector<uint8_t> sk(reinterpret_cast<uint8_t*>(skBytes),
                                reinterpret_cast<uint8_t*>(skBytes) + skLength);

        env->ReleaseByteArrayElements(message, msgBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(secretKey, skBytes, JNI_ABORT);

        auto signature = MLDSA87::sign(msg, sk);

        jbyteArray result = env->NewByteArray(signature.size());
        env->SetByteArrayRegion(result, 0, signature.size(),
                                reinterpret_cast<const jbyte*>(signature.data()));

        return result;
    } catch (const std::exception& e) {
        LOGE("ML-DSA-87 signing failed: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_im_molly_security_MLDSA87_nativeVerify(JNIEnv* env, jclass /* clazz */,
    jbyteArray message, jbyteArray signature, jbyteArray publicKey) {
    if (!message || !signature || !publicKey) return JNI_FALSE;

    try {
        jbyte* msgBytes = env->GetByteArrayElements(message, nullptr);
        jsize msgLength = env->GetArrayLength(message);
        jbyte* sigBytes = env->GetByteArrayElements(signature, nullptr);
        jsize sigLength = env->GetArrayLength(signature);
        jbyte* pkBytes = env->GetByteArrayElements(publicKey, nullptr);
        jsize pkLength = env->GetArrayLength(publicKey);

        std::vector<uint8_t> msg(reinterpret_cast<uint8_t*>(msgBytes),
                                 reinterpret_cast<uint8_t*>(msgBytes) + msgLength);
        std::vector<uint8_t> sig(reinterpret_cast<uint8_t*>(sigBytes),
                                 reinterpret_cast<uint8_t*>(sigBytes) + sigLength);
        std::vector<uint8_t> pk(reinterpret_cast<uint8_t*>(pkBytes),
                                reinterpret_cast<uint8_t*>(pkBytes) + pkLength);

        env->ReleaseByteArrayElements(message, msgBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(signature, sigBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(publicKey, pkBytes, JNI_ABORT);

        bool valid = MLDSA87::verify(msg, sig, pk);

        return valid ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("ML-DSA-87 verification failed: %s", e.what());
        return JNI_FALSE;
    }
}

} // extern "C"
