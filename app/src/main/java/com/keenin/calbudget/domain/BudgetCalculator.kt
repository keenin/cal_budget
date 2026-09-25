package com.keenin.calbudget.domain

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.EventKind
import java.time.LocalDate

data class BudgetSnapshot(
    val nextPayday: LocalDate?,
    /** Payday after [nextPayday]. End of the next paycheck period. Null when none remains. */
    val followingPayday: LocalDate?,
    /** Unpaid obligations due from today through [nextPayday], inclusive. */
    val untilPaydayCents: Long,
    /** Unpaid obligations due strictly after [nextPayday] through [followingPayday], inclusive. */
    val nextPeriodCents: Long,
    val untilPaydayCount: Int,
    val nextPeriodCount: Int,
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
     * Two obligation totals. Neither includes paycheck amounts.
     *
     * Until payday: today through the next payday, inclusive. If today is a payday,
     * that next payday is the following one.
     * Next period: strictly after that payday through the payday after it. Zero when
     * there is no later payday.
     */
    fun calculate(
        events: List<CashEventEntity>,
        cards: List<CreditCardEntity>,
        today: LocalDate,
    ): BudgetSnapshot {
        val payday = nextPayday(events, today)
            ?: return BudgetSnapshot(
                nextPayday = null,
                followingPayday = null,
                untilPaydayCents = 0,
                nextPeriodCents = 0,
                untilPaydayCount = 0,
                nextPeriodCount = 0,
            )
        val following = paydayAfter(events, payday)
        val until = sumCash(events, today, payday)
        val next = if (following == null) {
            WindowTotal(0, 0)
        } else {
            sumCash(events, payday.plusDays(1), following)
        }

        var untilCents = until.cents
        var untilCount = until.count
        var nextCents = next.cents
        var nextCount = next.count
        for (card in cards) {
            val remaining = remainingOwed(card)
            if (remaining <= 0L) continue
            val due = balanceDueDate(card, today)
            when {
                !due.isAfter(payday) -> {
                    untilCents += remaining
                    untilCount += 1
                }
                following != null && !due.isAfter(following) -> {
                    nextCents += remaining
                    nextCount += 1
                }
            }
        }
        return BudgetSnapshot(
            nextPayday = payday,
            followingPayday = following,
            untilPaydayCents = untilCents,
            nextPeriodCents = nextCents,
            untilPaydayCount = untilCount,
            nextPeriodCount = nextCount,
        )
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

    /** Statement balance still unpaid for the current cycle. Overpay counts as zero. */
    fun remainingOwed(card: CreditCardEntity): Long {
        return (card.amountCents - card.paidTowardCents).coerceAtLeast(0L)
    }

    /** Add a payment toward the current statement. Does not change the statement balance. */
    fun recordPayment(card: CreditCardEntity, paymentCents: Long): CreditCardEntity {
        if (paymentCents <= 0L) return card
        return card.copy(paidTowardCents = card.paidTowardCents + paymentCents)
    }

    fun clearPayment(card: CreditCardEntity): CreditCardEntity {
        return card.copy(paidTowardCents = 0L)
    }

    /**
     * Store a statement balance. A new cycle drops payments from the previous one.
     * The same cycle keeps them, so a corrected balance still subtracts what was paid.
     */
    fun applyStatementBalance(
        card: CreditCardEntity,
        amountCents: Long,
        cycleKey: String,
    ): CreditCardEntity {
        val paid = if (card.lastCapturedCycleKey == cycleKey) card.paidTowardCents else 0L
        return card.copy(
            amountCents = amountCents,
            lastCapturedCycleKey = cycleKey,
            paidTowardCents = paid,
        )
    }

    /** Payments survive an edit only while the statement cycle stays the same. */
    fun paidTowardForSave(existing: CreditCardEntity?, cycleKey: String?): Long {
        if (existing == null || cycleKey != existing.lastCapturedCycleKey) return 0L
        return existing.paidTowardCents
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

    /** Occurrences in the window that are still unpaid. */
    fun unpaidOccurrences(event: CashEventEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
        val paidThrough = event.paidThroughEpochDay
        return Schedule.occurrencesBetween(event, from, to).filter { date ->
            paidThrough == null || date.toEpochDay() > paidThrough
        }
    }

    /** Next due date that is not covered by [CashEventEntity.paidThroughEpochDay]. */
    fun nextUnpaidOccurrence(event: CashEventEntity, today: LocalDate): LocalDate? {
        val paidThrough = event.paidThroughEpochDay?.let(LocalDate::ofEpochDay)
        val from = when {
            paidThrough == null || paidThrough.isBefore(today) -> today
            else -> paidThrough.plusDays(1)
        }
        return Schedule.nextOnOrAfter(event, from)
    }

    /**
     * The nearest payday on or after today. If today is itself a payday, this is
     * the following one, so the current check does not close the first window.
     */
    fun nextPayday(events: List<CashEventEntity>, today: LocalDate): LocalDate? {
        val pays = events.filter { it.kind == EventKind.PAY }
        val upcoming = pays.mapNotNull { Schedule.nextOnOrAfter(it, today) }.minOrNull() ?: return null
        if (upcoming.isAfter(today)) return upcoming
        return pays.mapNotNull { Schedule.nextOnOrAfter(it, today.plusDays(1)) }.minOrNull() ?: upcoming
    }

    /** The payday after [payday], across every pay schedule. */
    fun paydayAfter(events: List<CashEventEntity>, payday: LocalDate): LocalDate? {
        val pays = events.filter { it.kind == EventKind.PAY }
        return pays.mapNotNull { Schedule.nextOnOrAfter(it, payday.plusDays(1)) }.minOrNull()
    }

    private fun sumCash(events: List<CashEventEntity>, from: LocalDate, to: LocalDate): WindowTotal {
        var cents = 0L
        var count = 0
        for (event in events) {
            if (event.kind == EventKind.PAY) continue
            val dates = unpaidOccurrences(event, from, to)
            if (dates.isNotEmpty()) {
                cents += event.amountCents * dates.size
                count += dates.size
            }
        }
        return WindowTotal(cents, count)
    }

    private data class WindowTotal(val cents: Long, val count: Int)
}
