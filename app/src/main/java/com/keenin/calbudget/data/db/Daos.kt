package com.keenin.calbudget.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashEventDao {
    @Query("SELECT * FROM cash_events ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CashEventEntity>>

    @Query("SELECT * FROM cash_events WHERE id = :id")
    suspend fun getById(id: Long): CashEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CashEventEntity): Long

    @Query("DELETE FROM cash_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cash_events")
    suspend fun deleteAll()
}

@Dao
interface CreditCardDao {
    @Query("SELECT * FROM credit_cards ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getById(id: Long): CreditCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CreditCardEntity): Long

    @Query("DELETE FROM credit_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM credit_cards")
    suspend fun deleteAll()
}
