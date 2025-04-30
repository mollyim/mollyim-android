package org.signal.cashu.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.signal.cashu.service.Transaction
import org.signal.cashu.service.TransactionStatus
import org.signal.cashu.service.TransactionType

@Database(
    entities = [Transaction::class, MintUrl::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CashuDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun mintUrlDao(): MintUrlDao
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val timestamp: Long,
    val type: TransactionType,
    val status: TransactionStatus,
    val memo: String? = null
)

@Entity(tableName = "mint_urls")
data class MintUrl(
    @PrimaryKey val url: String,
    val name: String,
    val isDefault: Boolean = false
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND status = :status")
    suspend fun getTotalAmount(type: TransactionType, status: TransactionStatus): Long
}

@Dao
interface MintUrlDao {
    @Query("SELECT * FROM mint_urls ORDER BY isDefault DESC")
    fun getAllMintUrls(): Flow<List<MintUrl>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMintUrl(mintUrl: MintUrl)

    @Query("SELECT * FROM mint_urls WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultMintUrl(): MintUrl?

    @Query("UPDATE mint_urls SET isDefault = 0")
    suspend fun clearDefaultMintUrl()

    @Query("UPDATE mint_urls SET isDefault = 1 WHERE url = :url")
    suspend fun setDefaultMintUrl(url: String)
}

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus {
        return TransactionStatus.valueOf(value)
    }
}