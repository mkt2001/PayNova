package com.paynova.upi.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class TxStatus { PENDING, SUCCESS, FAILED, REVERSED }

@Entity(tableName = "transactions")
data class TxEntity(
    @PrimaryKey val id: String,
    val counterpartyName: String,
    val counterpartyVpa: String,
    val amountPaise: Long,
    val direction: String,      // SENT | RECEIVED | REFUND
    val status: String,         // TxStatus name
    val method: String,
    val note: String,
    val category: String,
    val createdAt: Long,
    val providerRef: String,
    val upiTxnId: String,
    val maskedAccount: String,
    val demo: Boolean,
    val idempotencyKey: String
)

fun TxEntity.title(): String = counterpartyName.ifBlank { counterpartyVpa }

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: String): Flow<TxEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TxEntity?

    @Query("SELECT * FROM transactions WHERE idempotencyKey = :key LIMIT 1")
    suspend fun byKey(key: String): TxEntity?

    @Query("SELECT * FROM transactions WHERE status = 'PENDING'")
    suspend fun pending(): List<TxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: TxEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Database(entities = [TxEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun txDao(): TxDao
}
