package org.signal.cashu.service

data class Transaction(
    val id: String,
    val amount: Long,
    val timestamp: Long,
    val type: TransactionType,
    val status: TransactionStatus,
    val memo: String? = null
)

enum class TransactionType {
    SEND,
    RECEIVE
}

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}