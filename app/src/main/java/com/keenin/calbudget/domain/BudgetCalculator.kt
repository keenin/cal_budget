package com.keenin.calbudget.domain

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.EventKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ObligationSource {
    BILL,
    MORTGAGE,
    CARD,
}

data class BillPayChoices(
    val markOn: LocalDate?,
    val canUndo: Boolean,
)

data class ObligationLine(
    val name: String,
    val due: LocalDate,
    val amountCents: Long,
    val overdue: Boolean,
    val sourceId: Long,
    val source: ObligationSource,
)

data class BudgetSnapshot(
    val nextPayday: LocalDate?,
    /** Payday after [nextPayday]. End of the next paycheck period. Null when none remains. */
    val followingPayday: LocalDate?,
    /** Payday after [followingPayday]. End of the period after next. Null when none remains. */
    val thirdPayday: LocalDate? = null,
    /** Unpaid obligations due from today through [nextPayday], inclusive. */
    val untilPayday: List<ObligationLine> = emptyList(),
    /** Unpaid obligations due strictly after [nextPayday] through [followingPayday], inclusive. */
    val nextPeriod: List<ObligationLine> = emptyList(),
    /** Unpaid obligations due strictly after [followingPayday] through [thirdPayday], inclusive. */
    val followingPeriod: List<ObligationLine> = emptyList(),
) {
    val untilPaydayCents: Long get() = untilPayday.sumOf { it.amountCents }
    val nextPeriodCents: Long get() = nextPeriod.sumOf { it.amountCents }
    val followingPeriodCents: Long get() = followingPeriod.sumOf { it.amountCents }
    val untilPaydayCount: Int get() = untilPayday.size
    val nextPeriodCount: Int get() = nextPeriod.size
    val followingPeriodCount: Int get() = followingPeriod.size

    /** Today through [nextPayday], or null when there is no payday. */
    fun untilSpan(today: LocalDate): String? = span(today, nextPayday)

    /** The day after [nextPayday] through [followingPayday]. */
    fun nextSpan(): String? {
        val start = nextPayday?.plusDays(1) ?: return null
        return span(start, followingPayday)
    }

    /** The day after [followingPayday] through [thirdPayday]. */
    fun followingSpan(): String? {
        val start = followingPayday?.plusDays(1) ?: return null
        return span(start, thirdPayday)
    }

    private fun span(start: LocalDate, end: LocalDate?): String? {
        if (end == null || end.isBefore(start)) return null
        val startText = start.format(windowDateFormatter)
        if (start == end) return startText
        return "$startText – ${end.format(windowDateFormatter)}"
    }

    private companion object {
        val windowDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    }
}

data class StatementPrompt(
    val cardId: Long,
    val cardName: String,
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val cycleKey: String,
)

object BudgetCalculator {
    /**
     * Three obligation totals. None includes paycheck amounts.
     *
     * Until payday: today through the next payday, inclusive. If today is a payday,
     * that next payday is the following one.
     * Next period: strictly after that payday through the payday after it.
     * Following period: strictly after that second payday through the third payday.
     * A missing later payday makes that window zero.
     */
    fun calculate(
        events: List<CashEventEntity>,
        cards: List<CreditCardEntity>,
        today: LocalDate,
    ): BudgetSnapshot {
        val payday = nextPayday(events, today)
            ?: return BudgetSnapshot(nextPayday = null, followingPayday = null)
        val following = paydayAfter(events, payday)
        val third = following?.let { paydayAfter(events, it) }
        val until = windowLines(events, cards, today, today, payday, includeEarlierCards = true)
        val next = if (following == null) {
            emptyList()
        } else {
            windowLines(events, cards, today, payday.plusDays(1), following, includeEarlierCards = false)
        }
        val afterNext = if (following == null || third == null) {
            emptyList()
        } else {
            windowLines(events, cards, today, following.plusDays(1), third, includeEarlierCards = false)
        }
        return BudgetSnapshot(
            nextPayday = payday,
            followingPayday = following,
            thirdPayday = third,
            untilPayday = until,
            nextPeriod = next,
            followingPeriod = afterNext,
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
    fun unpaidOccurrences(event: CashEventEntity, from: LocalDate, to: LocalDate, today: LocalDate): List<LocalDate> {
        return Schedule.occurrencesBetween(event, from, to).filter { date ->
            stillOwed(event, date, today)
        }
    }

    /**
     * An occurrence is still owed unless it was marked paid, or automatic payment
     * has reached its due date. On the due date itself it is assumed paid.
     */
    fun stillOwed(event: CashEventEntity, date: LocalDate, today: LocalDate): Boolean {
        val paidThrough = event.paidThroughEpochDay
        if (paidThrough != null && date.toEpochDay() <= paidThrough) return false
        if (event.autoPay && !date.isAfter(today)) return false
        return true
    }

    /**
     * What the breakdown sheet should offer for one bill or housing row.
     * [markOn] is the next unpaid date when this row is still unpaid. Undo is available
     * once any occurrence has been marked paid.
     */
    fun billPayChoices(event: CashEventEntity, rowDue: LocalDate, today: LocalDate): BillPayChoices {
        val paidThrough = event.paidThroughEpochDay
        val rowPaid = !stillOwed(event, rowDue, today)
        return BillPayChoices(
            markOn = if (rowPaid) null else nextUnpaidOccurrence(event, today),
            canUndo = paidThrough != null,
        )
    }

    /** Next due date that is not covered by [CashEventEntity.paidThroughEpochDay]. */
    fun nextUnpaidOccurrence(event: CashEventEntity, today: LocalDate): LocalDate? {
        val paidThrough = event.paidThroughEpochDay?.let(LocalDate::ofEpochDay)
        var from = when {
            paidThrough == null || paidThrough.isBefore(today) -> today
            else -> paidThrough.plusDays(1)
        }
        if (event.autoPay && !from.isAfter(today)) {
            from = today.plusDays(1)
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

    private fun windowLines(
        events: List<CashEventEntity>,
        cards: List<CreditCardEntity>,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
        includeEarlierCards: Boolean,
    ): List<ObligationLine> {
        val lines = ArrayList<ObligationLine>()
        for (event in events) {
            if (event.kind == EventKind.PAY) continue
            for (date in unpaidOccurrences(event, from, to, today)) {
                lines += ObligationLine(
                    name = event.name,
                    due = date,
                    amountCents = event.amountCents,
                    overdue = date.isBefore(today),
                    sourceId = event.id,
                    source = if (event.kind == EventKind.MORTGAGE) {
                        ObligationSource.MORTGAGE
                    } else {
                        ObligationSource.BILL
                    },
                )
            }
        }
        for (card in cards) {
            val remaining = remainingOwed(card)
            if (remaining <= 0L) continue
            val due = balanceDueDate(card, today)
            if (card.autoPay && !due.isAfter(today)) continue
            val earlierThanWindow = !includeEarlierCards && due.isBefore(from)
            if (earlierThanWindow || due.isAfter(to)) continue
            lines += ObligationLine(
                name = card.name,
                due = due,
                amountCents = remaining,
                overdue = due.isBefore(today),
                sourceId = card.id,
                source = ObligationSource.CARD,
            )
        }
        return lines.sortedWith(compareBy({ it.due }, { it.name.lowercase() }))
    }
}
