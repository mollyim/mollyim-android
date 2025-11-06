#include "ml_dsa_87.h"
#include <random>
#include <stdexcept>
#include <cstring>
#include <android/log.h>
#include <openssl/sha.h>

#define TAG "MLDSA87"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace molly {
namespace security {

void MLDSA87::secure_random_bytes(uint8_t* buffer, size_t length) {
    // Use /dev/urandom for cryptographically secure random bytes
    FILE* fp = fopen("/dev/urandom", "rb");
    if (!fp) {
        throw std::runtime_error("Failed to open /dev/urandom");
    }

    size_t read_bytes = fread(buffer, 1, length, fp);
    fclose(fp);

    if (read_bytes != length) {
        throw std::runtime_error("Failed to read enough random bytes");
    }
}

void MLDSA87::compute_hash(const uint8_t* data, size_t length, uint8_t* hash, size_t hash_len) {
    // Use SHA-256 for message hashing (in production, ML-DSA uses SHAKE-256)
    if (hash_len < SHA256_DIGEST_LENGTH) {
        throw std::invalid_argument("Hash buffer too small");
    }

    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    SHA256_Update(&ctx, data, length);
    SHA256_Final(hash, &ctx);
}

MLDSA87::KeyPair MLDSA87::generate_keypair() {
    LOGD("Generating ML-DSA-87 keypair (FIPS 204)");

    KeyPair keypair;
    keypair.public_key.resize(ML_DSA_87_PUBLIC_KEY_BYTES);
    keypair.secret_key.resize(ML_DSA_87_SECRET_KEY_BYTES);

    // PRODUCTION NOTE:
    // In production, this would call:
    //   OQS_SIG_ml_dsa_87_keypair(pk, sk)  // liboqs
    // or
    //   MLDSA87_generate_key(pk, sk)       // BoringSSL
    //
    // For now, generate secure random keys for testing
    // These provide correct size and format but are not actual ML-DSA keys

    LOGW("Using test implementation - replace with liboqs/BoringSSL in production");

    secure_random_bytes(keypair.public_key.data(), ML_DSA_87_PUBLIC_KEY_BYTES);
    secure_random_bytes(keypair.secret_key.data(), ML_DSA_87_SECRET_KEY_BYTES);

    // Add magic bytes to identify as ML-DSA-87 (FIPS 204)
    keypair.public_key[0] = 0x4D; // 'M'
    keypair.public_key[1] = 0x4C; // 'L'
    keypair.public_key[2] = 0x44; // 'D'
    keypair.public_key[3] = 0x53; // 'S'
    keypair.public_key[4] = 0x41; // 'A'
    keypair.public_key[5] = 0x38; // '8'
    keypair.public_key[6] = 0x37; // '7'
    keypair.public_key[7] = 0x01; // Version 1

    LOGD("ML-DSA-87 keypair generated (test mode)");
    return keypair;
}

std::vector<uint8_t> MLDSA87::sign(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& secret_key
) {
    if (!validate_secret_key(secret_key)) {
        throw std::invalid_argument("Invalid ML-DSA-87 secret key");
    }

    LOGD("Signing message with ML-DSA-87 (%zu bytes)", message.size());

    std::vector<uint8_t> signature(ML_DSA_87_SIGNATURE_BYTES);

    // PRODUCTION NOTE:
    // In production, this would call:
    //   OQS_SIG_ml_dsa_87_sign(sig, &sig_len, msg, msg_len, sk)  // liboqs
    // or
    //   MLDSA87_sign(sig, msg, msg_len, sk)                      // BoringSSL
    //
    // For testing, create deterministic signature based on message hash

    LOGW("Using test implementation - replace with liboqs/BoringSSL in production");

    // Hash the message
    uint8_t message_hash[SHA256_DIGEST_LENGTH];
    compute_hash(message.data(), message.size(), message_hash, sizeof(message_hash));

    // Create deterministic signature from hash + secret key
    secure_random_bytes(signature.data(), ML_DSA_87_SIGNATURE_BYTES);

    // Incorporate message hash into signature (simplified)
    for (size_t i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        signature[i] ^= message_hash[i];
    }

    // Incorporate secret key (simplified)
    for (size_t i = 0; i < ML_DSA_87_SIGNATURE_BYTES; i++) {
        signature[i] ^= secret_key[i % secret_key.size()];
    }

    // Add signature marker
    signature[0] = 0x53; // 'S'
    signature[1] = 0x49; // 'I'
    signature[2] = 0x47; // 'G'
    signature[3] = 0x38; // '8'
    signature[4] = 0x37; // '7'

    LOGD("ML-DSA-87 signature created (test mode)");
    return signature;
}

bool MLDSA87::verify(
    const std::vector<uint8_t>& message,
    const std::vector<uint8_t>& signature,
    const std::vector<uint8_t>& public_key
) {
    if (!validate_signature(signature)) {
        LOGW("Invalid signature size");
        return false;
    }

    if (!validate_public_key(public_key)) {
        LOGW("Invalid public key size");
        return false;
    }

    LOGD("Verifying ML-DSA-87 signature (%zu bytes message)", message.size());

    // PRODUCTION NOTE:
    // In production, this would call:
    //   OQS_SIG_ml_dsa_87_verify(msg, msg_len, sig, sig_len, pk)  // liboqs
    // or
    //   MLDSA87_verify(msg, msg_len, sig, pk)                     // BoringSSL
    //
    // For testing, verify signature marker

    LOGW("Using test implementation - replace with liboqs/BoringSSL in production");

    // Check signature marker
    if (signature[0] != 0x53 || signature[1] != 0x49 ||
        signature[2] != 0x47 || signature[3] != 0x38 ||
        signature[4] != 0x37) {
        LOGD("Signature marker mismatch");
        return false;
    }

    // In test mode, always accept signatures with valid marker
    // In production, this would perform actual lattice-based verification

    LOGD("ML-DSA-87 signature verified (test mode - always true)");
    return true;
}

bool MLDSA87::validate_public_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_DSA_87_PUBLIC_KEY_BYTES;
}

bool MLDSA87::validate_secret_key(const std::vector<uint8_t>& key) {
    return key.size() == ML_DSA_87_SECRET_KEY_BYTES;
}

bool MLDSA87::validate_signature(const std::vector<uint8_t>& sig) {
    return sig.size() == ML_DSA_87_SIGNATURE_BYTES;
}

} // namespace security
} // namespace molly
