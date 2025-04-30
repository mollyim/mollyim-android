package org.signal.cashu.model

import java.util.Date

data class Transaction(
    val amount: Long,
    val description: String,
    val date: Date
)