package com.keenin.calbudget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keenin.calbudget.data.BudgetRepository
import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.EventKind
import com.keenin.calbudget.domain.BudgetCalculator
import com.keenin.calbudget.domain.Schedule
import com.keenin.calbudget.domain.BudgetSnapshot
import com.keenin.calbudget.domain.StatementPrompt
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetUi(
    val loading: Boolean,
    val events: List<CashEventEntity>,
    val cards: List<CreditCardEntity>,
    val today: LocalDate,
    val snapshot: BudgetSnapshot,
    val prompts: List<StatementPrompt>,
) {
    companion object {
        fun loading(): BudgetUi {
            val today = LocalDate.now()
            return BudgetUi(
                loading = true,
                events = emptyList(),
                cards = emptyList(),
                today = today,
                snapshot = BudgetSnapshot(
                    nextPayday = null,
                    followingPayday = null,
                    untilPaydayCents = 0,
                    nextPeriodCents = 0,
                    untilPaydayCount = 0,
                    nextPeriodCount = 0,
                ),
                prompts = emptyList(),
            )
        }
    }
}

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now())
    private val snoozedCardIds = MutableStateFlow<Set<Long>>(emptySet())

    val ui: StateFlow<BudgetUi> = combine(
        repository.observeEvents(),
        repository.observeCards(),
        today,
        snoozedCardIds,
    ) { events, cards, day, snoozed ->
        BudgetUi(
            loading = false,
            events = events,
            cards = cards,
            today = day,
            snapshot = BudgetCalculator.calculate(events, cards, day),
            prompts = BudgetCalculator.pendingStatements(cards, day, snoozed),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BudgetUi.loading())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun skipStatement(cardId: Long) {
        snoozedCardIds.update { it + cardId }
    }

    fun captureStatement(cardId: Long, amountCents: Long, cycleKey: String) {
        viewModelScope.launch {
            val card = repository.getCard(cardId) ?: return@launch
            repository.upsertCard(BudgetCalculator.applyStatementBalance(card, amountCents, cycleKey))
        }
    }

    fun recordCardPayment(cardId: Long, paymentCents: Long) {
        if (paymentCents <= 0L) return
        viewModelScope.launch {
            val card = repository.getCard(cardId) ?: return@launch
            repository.upsertCard(BudgetCalculator.recordPayment(card, paymentCents))
        }
    }

    fun clearCardPayment(cardId: Long) {
        viewModelScope.launch {
            val card = repository.getCard(cardId) ?: return@launch
            repository.upsertCard(BudgetCalculator.clearPayment(card))
        }
    }

    fun markNextOccurrencePaid(id: Long) {
        viewModelScope.launch {
            val event = repository.getEvent(id) ?: return@launch
            if (event.kind == EventKind.PAY) return@launch
            val next = BudgetCalculator.nextUnpaidOccurrence(event, today.value) ?: return@launch
            repository.upsertEvent(event.copy(paidThroughEpochDay = next.toEpochDay()))
        }
    }

    fun undoLastPaidOccurrence(id: Long) {
        viewModelScope.launch {
            val event = repository.getEvent(id) ?: return@launch
            val paidThrough = event.paidThroughEpochDay ?: return@launch
            val previous = Schedule.previousStrictlyBefore(event, LocalDate.ofEpochDay(paidThrough))
            repository.upsertEvent(event.copy(paidThroughEpochDay = previous?.toEpochDay()))
        }
    }

    fun saveEvent(event: CashEventEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertEvent(event)
            onDone()
        }
    }

    fun deleteEvent(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            onDone()
        }
    }

    fun saveCard(card: CreditCardEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = if (card.id == 0L) null else repository.getCard(card.id)
            repository.upsertCard(
                card.copy(paidTowardCents = BudgetCalculator.paidTowardForSave(existing, card.lastCapturedCycleKey)),
            )
            onDone()
        }
    }

    fun deleteCard(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCard(id)
            snoozedCardIds.update { it - id }
            onDone()
        }
    }

    fun clearAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            snoozedCardIds.value = emptySet()
            onDone()
        }
    }
}

class BudgetViewModelFactory(private val repository: BudgetRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BudgetViewModel(repository) as T
    }
}
