package org.signal.cashu.service

import okhttp3.OkHttpClient

interface CashuService {
    fun getBalance(): Long
    fun sendPayment(amount: Long, recipient: String): Boolean
    fun receivePayment(token: String): Boolean
    fun getTransactions(): List<Transaction>
}

class DefaultCashuService(private val httpClient: OkHttpClient) : CashuService {
    override fun getBalance(): Long {
        // TODO: Implement actual balance check
        return 0
    }

    override fun sendPayment(amount: Long, recipient: String): Boolean {
        // TODO: Implement actual payment sending
        return false
    }

    override fun receivePayment(token: String): Boolean {
        // TODO: Implement actual payment receiving
        return false
    }

    override fun getTransactions(): List<Transaction> {
        // TODO: Implement actual transaction fetching
        return emptyList()
    }
}