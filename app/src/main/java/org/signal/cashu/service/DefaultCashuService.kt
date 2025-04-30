package org.signal.cashu.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.signal.cashu.database.CashuDatabase
import org.signal.cashu.database.MintUrl
import org.signal.cashu.database.TransactionEntity
import java.util.UUID

class DefaultCashuService(
    private val httpClient: OkHttpClient,
    private val database: CashuDatabase
) : CashuService {
    private val transactionDao = database.transactionDao()
    private val mintUrlDao = database.mintUrlDao()

    override fun getBalance(): Long {
        return runBlocking {
            val completedReceives = transactionDao.getTotalAmount(
                TransactionType.RECEIVE,
                TransactionStatus.COMPLETED
            )
            val completedSends = transactionDao.getTotalAmount(
                TransactionType.SEND,
                TransactionStatus.COMPLETED
            )
            completedReceives - completedSends
        }
    }

    override suspend fun sendPayment(amount: Long, recipient: String): Boolean {
        val defaultMint = mintUrlDao.getDefaultMintUrl() ?: return false

        val transaction = TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = amount,
            timestamp = System.currentTimeMillis(),
            type = TransactionType.SEND,
            status = TransactionStatus.PENDING
        )

        try {
            transactionDao.insertTransaction(transaction)

            val requestBody = JSONObject().apply {
                put("amount", amount)
                put("recipient", recipient)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${defaultMint.url}/send")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                transactionDao.insertTransaction(transaction.copy(status = TransactionStatus.COMPLETED))
                return true
            } else {
                transactionDao.insertTransaction(transaction.copy(status = TransactionStatus.FAILED))
                return false
            }
        } catch (e: Exception) {
            transactionDao.insertTransaction(transaction.copy(status = TransactionStatus.FAILED))
            return false
        }
    }

    override suspend fun receivePayment(token: String): Boolean {
        val defaultMint = mintUrlDao.getDefaultMintUrl() ?: return false

        val transaction = TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = 0, // Will be updated after successful receive
            timestamp = System.currentTimeMillis(),
            type = TransactionType.RECEIVE,
            status = TransactionStatus.PENDING
        )

        try {
            transactionDao.insertTransaction(transaction)

            val requestBody = JSONObject().apply {
                put("token", token)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${defaultMint.url}/receive")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val amount = JSONObject(responseBody).getLong("amount")

                transactionDao.insertTransaction(
                    transaction.copy(
                        amount = amount,
                        status = TransactionStatus.COMPLETED
                    )
                )
                return true
            } else {
                transactionDao.insertTransaction(transaction.copy(status = TransactionStatus.FAILED))
                return false
            }
        } catch (e: Exception) {
            transactionDao.insertTransaction(transaction.copy(status = TransactionStatus.FAILED))
            return false
        }
    }

    override fun getTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { entity ->
                Transaction(
                    id = entity.id,
                    amount = entity.amount,
                    timestamp = entity.timestamp,
                    type = entity.type,
                    status = entity.status,
                    memo = entity.memo
                )
            }
        }
    }

    suspend fun addMintUrl(url: String, name: String, isDefault: Boolean = false) {
        if (isDefault) {
            mintUrlDao.clearDefaultMintUrl()
        }
        mintUrlDao.insertMintUrl(MintUrl(url, name, isDefault))
    }

    fun getAllMintUrls(): Flow<List<MintUrl>> {
        return mintUrlDao.getAllMintUrls()
    }
}