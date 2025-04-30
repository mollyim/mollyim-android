package org.signal.cashu.util

import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.math.ec.ECPoint
import org.signal.cashu.model.BlindedMessage
import org.signal.cashu.model.Proof
import java.math.BigInteger
import java.security.SecureRandom

object CryptoUtil {
    private val curve = ECNamedCurveTable.getParameterSpec("secp256k1")
    private val random = SecureRandom()

    fun createBlindedMessage(secret: ByteArray, amount: Long): BlindedMessage {
        // Generate random blinding factor
        val blindingFactor = BigInteger(256, random)

        // Create commitment
        val commitment = createCommitment(secret, amount)

        // Blind commitment
        val blindedCommitment = commitment.multiply(curve.g.multiply(blindingFactor))

        return BlindedMessage(
            amount = amount,
            blindedMessage = blindedCommitment.getEncoded(true).toHexString(),
            id = secret.toHexString()
        )
    }

    fun createProof(secret: ByteArray, signature: String, amount: Long): Proof {
        val commitment = createCommitment(secret, amount)

        return Proof(
            amount = amount,
            secret = secret.toHexString(),
            commitment = commitment.getEncoded(true).toHexString(),
            id = signature
        )
    }

    fun verifyProof(proof: Proof): Boolean {
        try {
            val commitment = curve.curve.decodePoint(proof.commitment.hexToBytes())
            val signature = proof.id.hexToBytes()

            // Verify signature
            val signer = ECDSASigner()
            signer.init(false, ECPublicKeyParameters(curve.g, curve))

            val r = BigInteger(1, signature.copyOfRange(0, 32))
            val s = BigInteger(1, signature.copyOfRange(32, 64))

            return signer.verifySignature(commitment.getEncoded(true), r, s)
        } catch (e: Exception) {
            return false
        }
    }

    private fun createCommitment(secret: ByteArray, amount: Long): ECPoint {
        val hash = hash(secret, amount)
        return curve.g.multiply(hash)
    }

    private fun hash(secret: ByteArray, amount: Long): BigInteger {
        val input = secret + amount.toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(input)
        return BigInteger(1, hash)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}