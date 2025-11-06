#include "ml_dsa_87.h"

#ifdef PRODUCTION_CRYPTO
// Production implementation using liboqs
#include <oqs/oqs.h>
#include <stdexcept>
#include <android/log.h>

#define TAG "MLDSA87_PROD"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace molly {
namespace security {

MLDSA87::KeyPair MLDSA87::generate_keypair() {
    LOGD("Generating ML-DSA-87 keypair (liboqs FIPS 204)");

    KeyPair keypair;
    keypair.public_key.resize(ML_DSA_87_PUBLIC_KEY_BYTES);
    keypair.secret_key.resize(ML_DSA_87_SECRET_KEY_BYTES);

    OQS_STATUS status = OQS_SIG_ml_dsa_87_keypair(
        keypair.public_key.data(),
        keypair.secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-DSA-87 keypair generation failed: OQS error %d", status);
        throw std::runtime_error("ML-DSA-87 keypair generation failed");
    }

    LOGI("ML-DSA-87 keypair generated successfully (PK: %zu bytes, SK: %zu bytes)",
         keypair.public_key.size(), keypair.secret_key.size());

    return keypair;
}

std::vector<uint8_t> MLDSA87::sign(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& secret_key
) {
    if (!validate_secret_key(secret_key)) {
        LOGE("Invalid ML-DSA-87 secret key size: %zu (expected %zu)",
             secret_key.size(), ML_DSA_87_SECRET_KEY_BYTES);
        throw std::invalid_argument("Invalid ML-DSA-87 secret key");
    }

    LOGD("Signing message with ML-DSA-87 (liboqs FIPS 204, %zu bytes)",
         message.size());

    std::vector<uint8_t> signature(ML_DSA_87_SIGNATURE_BYTES);
    size_t signature_length = signature.size();

    OQS_STATUS status = OQS_SIG_ml_dsa_87_sign(
        signature.data(),
        &signature_length,
        message.data(),
        message.size(),
        secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-DSA-87 signing failed: OQS error %d", status);
        throw std::runtime_error("ML-DSA-87 signing failed");
    }

    // Resize to actual signature length (may be smaller than max)
    signature.resize(signature_length);

    LOGI("ML-DSA-87 signature created (%zu bytes for %zu byte message)",
         signature_length, message.size());

    return signature;
}

bool MLDSA87::verify(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& signature,
    const std::vector<uint8_t>& public_key
) {
    if (!validate_signature(signature)) {
        LOGE("Invalid ML-DSA-87 signature size: %zu (expected %zu)",
             signature.size(), ML_DSA_87_SIGNATURE_BYTES);
        return false;
    }

    if (!validate_public_key(public_key)) {
        LOGE("Invalid ML-DSA-87 public key size: %zu (expected %zu)",
             public_key.size(), ML_DSA_87_PUBLIC_KEY_BYTES);
        return false;
    }

    LOGD("Verifying ML-DSA-87 signature (liboqs FIPS 204)");

    OQS_STATUS status = OQS_SIG_ml_dsa_87_verify(
        message.data(),
        message.size(),
        signature.data(),
        signature.size(),
        public_key.data()
    );

    bool valid = (status == OQS_SUCCESS);

    if (valid) {
        LOGI("ML-DSA-87 signature VALID for %zu byte message", message.size());
    } else {
        LOGE("ML-DSA-87 signature INVALID for %zu byte message (OQS error %d)",
             message.size(), status);
    }

    return valid;
}

bool MLDSA87::validate_public_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_DSA_87_PUBLIC_KEY_BYTES;
}

bool MLDSA87::validate_secret_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_DSA_87_SECRET_KEY_BYTES;
}

bool MLDSA87::validate_signature(const std::vector<uint8_t>& sig) {
    // ML-DSA-87 signatures can be up to ML_DSA_87_SIGNATURE_BYTES
    // but may be smaller due to compression
    return sig.size() > 0 && sig.size() <= ML_DSA_87_SIGNATURE_BYTES;
}

// Stub implementations for secure_random_bytes and compute_hash
// These are not needed in production since liboqs handles everything
void MLDSA87::secure_random_bytes(uint8_t* buffer, size_t length) {
    // Not used in production implementation
    (void)buffer;
    (void)length;
}

void MLDSA87::compute_hash(const uint8_t* data, size_t length,
                          uint8_t* hash, size_t hash_len) {
    // Not used in production implementation
    (void)data;
    (void)length;
    (void)hash;
    (void)hash_len;
}

} // namespace security
} // namespace molly

#else
// Test implementation - include the test version
#include "ml_dsa_87.cpp"
#endif // PRODUCTION_CRYPTO
