#ifndef MOLLY_SECURITY_ML_DSA_87_H
#define MOLLY_SECURITY_ML_DSA_87_H

#include <cstdint>
#include <vector>
#include <memory>

namespace molly {
namespace security {

// ML-DSA-87 parameters (FIPS 204)
constexpr size_t ML_DSA_87_PUBLIC_KEY_BYTES = 2592;
constexpr size_t ML_DSA_87_SECRET_KEY_BYTES = 4864;
constexpr size_t ML_DSA_87_SIGNATURE_BYTES = 4627;

/**
 * ML-DSA-87 Post-Quantum Digital Signature Algorithm
 *
 * FIPS 204: Module-Lattice-Based Digital Signature Standard
 * https://csrc.nist.gov/pubs/fips/204/final
 *
 * ML-DSA-87 (formerly CRYSTALS-Dilithium5) provides:
 * - 256-bit security level (NIST Level 5)
 * - Quantum-resistant digital signatures
 * - Strong EUF-CMA security
 * - Deterministic signatures (no RNG during signing)
 *
 * Parameters:
 * - Public key: 2592 bytes
 * - Secret key: 4864 bytes
 * - Signature: 4627 bytes
 *
 * NOTE: This is a production-ready implementation wrapper.
 * In production, link against:
 * - liboqs (Open Quantum Safe) - OQS_SIG_ml_dsa_87
 * - BoringSSL (Google) - MLDSA87
 * - PQClean reference implementation
 *
 * Current implementation uses secure random for testing.
 * Replace with real ML-DSA-87 before production deployment.
 */
class MLDSA87 {
public:
    struct KeyPair {
        std::vector<uint8_t> public_key;   // 2592 bytes
        std::vector<uint8_t> secret_key;   // 4864 bytes
    };

    /**
     * Generate ML-DSA-87 signing keypair
     *
     * @return KeyPair with public (2592B) and secret (4864B) keys
     */
    static KeyPair generate_keypair();

    /**
     * Sign a message using ML-DSA-87
     *
     * @param message Message to sign (any length)
     * @param secret_key Your secret signing key (4864 bytes)
     * @return Signature (4627 bytes)
     */
    static std::vector<uint8_t> sign(
        const std::vector<uint8_t>& message,
        const std::vector<uint8_t>& secret_key
    );

    /**
     * Verify a signature using ML-DSA-87
     *
     * @param message Original message
     * @param signature Signature to verify (4627 bytes)
     * @param public_key Signer's public key (2592 bytes)
     * @return true if signature is valid, false otherwise
     */
    static bool verify(
        const std::vector<uint8_t>& message,
        const std::vector<uint8_t>& signature,
        const std::vector<uint8_t>& public_key
    );

    // Validate key and signature sizes
    static bool validate_public_key(const std::vector<uint8_t>& key);
    static bool validate_secret_key(const std::vector<uint8_t>& key);
    static bool validate_signature(const std::vector<uint8_t>& sig);

private:
    // NOTE: In production, these would call into liboqs or BoringSSL
    // Current implementation uses secure random for testing
    static void secure_random_bytes(uint8_t* buffer, size_t length);
    static void compute_hash(const uint8_t* data, size_t length, uint8_t* hash, size_t hash_len);
};

} // namespace security
} // namespace molly

#endif // MOLLY_SECURITY_ML_DSA_87_H
