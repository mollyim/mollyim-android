package org.signal.cashu.service

import org.signal.cashu.model.Token

interface CashuService {
    suspend fun createToken(amount: Long, mintUrl: String): Token
    suspend fun redeemToken(token: Token, mintUrl: String): Boolean
    suspend fun verifyToken(token: Token, mintUrl: String): Boolean
    suspend fun splitToken(token: Token, amount: Long, mintUrl: String): List<Token>
    suspend fun combineTokens(tokens: List<Token>, mintUrl: String): Token
}