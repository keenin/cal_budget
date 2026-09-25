package com.keenin.calbudget.data

import com.keenin.calbudget.data.db.AppDatabase
import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val database: AppDatabase) {
    fun observeEvents(): Flow<List<CashEventEntity>> = database.cashEventDao().observeAll()

    fun observeCards(): Flow<List<CreditCardEntity>> = database.creditCardDao().observeAll()

    suspend fun getEvent(id: Long): CashEventEntity? = database.cashEventDao().getById(id)

    suspend fun getCard(id: Long): CreditCardEntity? = database.creditCardDao().getById(id)

    suspend fun upsertEvent(event: CashEventEntity) {
        database.cashEventDao().upsert(event)
    }

    suspend fun deleteEvent(id: Long) {
        database.cashEventDao().delete(id)
    }

    suspend fun upsertCard(card: CreditCardEntity) {
        database.creditCardDao().upsert(card)
    }

    suspend fun deleteCard(id: Long) {
        database.creditCardDao().delete(id)
    }

    suspend fun clearAll() {
        database.cashEventDao().deleteAll()
        database.creditCardDao().deleteAll()
    }
}
