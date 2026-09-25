package com.keenin.calbudget.domain

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.EventKind
import java.time.LocalDate

data class BudgetSnapshot(
    val nextPayday: LocalDate?,
    val amountCents: Long,
    val obligationCount: Int,
)

data class StatementPrompt(
    val cardId: Long,
    val cardName: String,
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val cycleKey: String,
)

object BudgetCalculator {
    /**
     * Obligations due from today through the next payday, inclusive.
     * When today is itself a payday, the window runs through the following payday
     * so the number is the cash needed until more pay arrives.
     */
    fun calculate(
        events: List<CashEventEntity>,
        cards: List<CreditCardEntity>,
        today: LocalDate,
    ): BudgetSnapshot {
        val payday = nextPayday(events, today)
            ?: return BudgetSnapshot(nextPayday = null, amountCents = 0, obligationCount = 0)

        var total = 0L
        var count = 0
        for (event in events) {
            if (event.kind == EventKind.PAY) continue
            val dates = Schedule.occurrencesBetween(event, today, payday)
            if (dates.isNotEmpty()) {
                total += event.amountCents * dates.size
                count += dates.size
            }
        }
        for (card in cards) {
            if (card.amountCents <= 0L) continue
            val due = balanceDueDate(card, today)
            if (!due.isAfter(payday)) {
                total += card.amountCents
                count += 1
            }
        }
        return BudgetSnapshot(nextPayday = payday, amountCents = total, obligationCount = count)
    }

    fun pendingStatements(
        cards: List<CreditCardEntity>,
        today: LocalDate,
        snoozedCardIds: Set<Long> = emptySet(),
    ): List<StatementPrompt> {
        return cards.mapNotNull { card ->
            if (card.id in snoozedCardIds) return@mapNotNull null
            val statement = Schedule.statementOnOrBefore(card.statementDay, today)
            val key = statement.toString()
            if (card.lastCapturedCycleKey == key) return@mapNotNull null
            StatementPrompt(
                cardId = card.id,
                cardName = card.name,
                statementDate = statement,
                dueDate = Schedule.paymentDueDate(
                    statementDay = card.statementDay,
                    dueMode = card.dueMode,
                    daysAfterStatement = card.daysAfterStatement,
                    dueDay = card.dueDay,
                    statementDate = statement,
                ),
                cycleKey = key,
            )
        }.sortedWith(compareBy({ it.statementDate }, { it.cardName.lowercase() }, { it.cardId }))
    }

    fun balanceDueDate(card: CreditCardEntity, today: LocalDate): LocalDate {
        val statement = card.lastCapturedCycleKey
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Schedule.statementOnOrBefore(card.statementDay, today)
        return Schedule.paymentDueDate(
            statementDay = card.statementDay,
            dueMode = card.dueMode,
            daysAfterStatement = card.daysAfterStatement,
            dueDay = card.dueDay,
            statementDate = statement,
        )
    }

    fun nextPayday(events: List<CashEventEntity>, today: LocalDate): LocalDate? {
        val pays = events.filter { it.kind == EventKind.PAY }
        val upcoming = pays.mapNotNull { Schedule.nextOnOrAfter(it, today) }.minOrNull() ?: return null
        if (upcoming.isAfter(today)) return upcoming
        return pays.mapNotNull { Schedule.nextOnOrAfter(it, today.plusDays(1)) }.minOrNull() ?: upcoming
    }
}
