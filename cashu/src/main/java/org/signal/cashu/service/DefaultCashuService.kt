package org.signal.cashu.service

import okhttp3.OkHttpClient
import okhttp3.Request
import org.signal.cashu.model.Token
import org.signal.cashu.util.CryptoUtil
import java.math.BigInteger
import java.security.SecureRandom

class DefaultCashuService(private val httpClient: OkHttpClient) : CashuService {
    private val random = SecureRandom()

    override suspend fun createToken(amount: Long, mintUrl: String): Token {
        // Generate random secret
        val secret = ByteArray(32).apply { random.nextBytes(this) }

        // Create blinded message
        val blindedMessage = CryptoUtil.createBlindedMessage(secret, amount)

        // Request signature from mint
        val signature = requestSignature(mintUrl, blindedMessage)

        // Create proof
        val proof = CryptoUtil.createProof(secret, signature, amount)

        return Token(
            token = listOf(blindedMessage),
            mint = mintUrl,
            proofs = listOf(proof)
        )
    }

    override suspend fun redeemToken(token: Token, mintUrl: String): Boolean {
        // Verify token
        if (!verifyToken(token, mintUrl)) {
            return false
        }

        // Request redemption from mint
        val response = httpClient.newCall(
            Request.Builder()
                .url("$mintUrl/redeem")
                .post(token.toJson())
                .build()
        ).execute()

        return response.isSuccessful
    }

    override suspend fun verifyToken(token: Token, mintUrl: String): Boolean {
        // Verify each proof
        return token.proofs.all { proof ->
            CryptoUtil.verifyProof(proof)
        }
    }

    override suspend fun splitToken(token: Token, amount: Long, mintUrl: String): List<Token> {
        // Verify token
        if (!verifyToken(token, mintUrl)) {
            return emptyList()
        }

        // Create two new tokens
        val token1 = createToken(amount, mintUrl)
        val token2 = createToken(token.proofs.sumOf { it.amount } - amount, mintUrl)

        return listOf(token1, token2)
    }

    override suspend fun combineTokens(tokens: List<Token>, mintUrl: String): Token {
        // Verify all tokens
        if (!tokens.all { verifyToken(it, mintUrl) }) {
            throw IllegalArgumentException("Invalid tokens")
        }

        // Create new token with combined amount
        val totalAmount = tokens.flatMap { it.proofs }.sumOf { it.amount }
        return createToken(totalAmount, mintUrl)
    }

    private suspend fun requestSignature(mintUrl: String, blindedMessage: BlindedMessage): String {
        val response = httpClient.newCall(
            Request.Builder()
                .url("$mintUrl/sign")
                .post(blindedMessage.toJson())
                .build()
        ).execute()

        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to get signature from mint")
        }

        return response.body?.string() ?: throw IllegalStateException("Empty response from mint")
    }
}