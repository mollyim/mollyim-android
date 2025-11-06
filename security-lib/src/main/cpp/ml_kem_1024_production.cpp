#include "ml_kem_1024.h"

#ifdef PRODUCTION_CRYPTO
// Production implementation using liboqs
#include <oqs/oqs.h>
#include <stdexcept>
#include <android/log.h>

#define TAG "MLKEM1024_PROD"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace molly {
namespace security {

MLKEM1024::KeyPair MLKEM1024::generate_keypair() {
    LOGD("Generating ML-KEM-1024 keypair (liboqs FIPS 203)");

    KeyPair keypair;
    keypair.public_key.resize(ML_KEM_1024_PUBLIC_KEY_BYTES);
    keypair.secret_key.resize(ML_KEM_1024_SECRET_KEY_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_keypair(
        keypair.public_key.data(),
        keypair.secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 keypair generation failed: OQS error %d", status);
        throw std::runtime_error("ML-KEM-1024 keypair generation failed");
    }

    LOGI("ML-KEM-1024 keypair generated successfully (PK: %zu bytes, SK: %zu bytes)",
         keypair.public_key.size(), keypair.secret_key.size());

    return keypair;
}

MLKEM1024::EncapsulationResult MLKEM1024::encapsulate(
    const std::vector<uint8_t>& public_key
) {
    if (!validate_public_key(public_key)) {
        LOGE("Invalid ML-KEM-1024 public key size: %zu (expected %zu)",
             public_key.size(), ML_KEM_1024_PUBLIC_KEY_BYTES);
        throw std::invalid_argument("Invalid ML-KEM-1024 public key");
    }

    LOGD("Encapsulating with ML-KEM-1024 (liboqs FIPS 203)");

    EncapsulationResult result;
    result.ciphertext.resize(ML_KEM_1024_CIPHERTEXT_BYTES);
    result.shared_secret.resize(ML_KEM_1024_SHARED_SECRET_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_encaps(
        result.ciphertext.data(),
        result.shared_secret.data(),
        public_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 encapsulation failed: OQS error %d", status);
        throw std::runtime_error("ML-KEM-1024 encapsulation failed");
    }

    LOGD("ML-KEM-1024 encapsulation successful (CT: %zu bytes, SS: %zu bytes)",
         result.ciphertext.size(), result.shared_secret.size());

    return result;
}

std::vector<uint8_t> MLKEM1024::decapsulate(
    const std::vector<uint8_t>& ciphertext,
    const std::vector<uint8_t>& secret_key
) {
    if (!validate_ciphertext(ciphertext)) {
        LOGE("Invalid ML-KEM-1024 ciphertext size: %zu (expected %zu)",
             ciphertext.size(), ML_KEM_1024_CIPHERTEXT_BYTES);
        throw std::invalid_argument("Invalid ML-KEM-1024 ciphertext");
    }

    if (!validate_secret_key(secret_key)) {
        LOGE("Invalid ML-KEM-1024 secret key size: %zu (expected %zu)",
             secret_key.size(), ML_KEM_1024_SECRET_KEY_BYTES);
        throw std::invalid_argument("Invalid ML-KEM-1024 secret key");
    }

    LOGD("Decapsulating with ML-KEM-1024 (liboqs FIPS 203)");

    std::vector<uint8_t> shared_secret(ML_KEM_1024_SHARED_SECRET_BYTES);

    OQS_STATUS status = OQS_KEM_ml_kem_1024_decaps(
        shared_secret.data(),
        ciphertext.data(),
        secret_key.data()
    );

    if (status != OQS_SUCCESS) {
        LOGE("ML-KEM-1024 decapsulation failed: OQS error %d", status);
        throw std::runtime_error("ML-KEM-1024 decapsulation failed");
    }

    LOGD("ML-KEM-1024 decapsulation successful (SS: %zu bytes)",
         shared_secret.size());

    return shared_secret;
}

bool MLKEM1024::validate_public_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_KEM_1024_PUBLIC_KEY_BYTES;
}

bool MLKEM1024::validate_secret_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_KEM_1024_SECRET_KEY_BYTES;
}

bool MLKEM1024::validate_ciphertext(const std::vector<uint8_t>& ct) {
    return ct.size() == ML_KEM_1024_CIPHERTEXT_BYTES;
}

} // namespace security
} // namespace molly

#else
// Test implementation - include the test version
#include "ml_kem_1024.cpp"
#endif // PRODUCTION_CRYPTO
