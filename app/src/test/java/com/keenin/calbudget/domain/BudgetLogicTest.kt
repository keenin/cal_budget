package com.keenin.calbudget.domain

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.CustomUnit
import com.keenin.calbudget.data.db.DueMode
import com.keenin.calbudget.data.db.EventKind
import com.keenin.calbudget.data.db.RecurrenceType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLogicTest {
    private val friday = LocalDate.of(2026, 9, 11)

    @Test
    fun biweeklyAnchorsOnStartDate() {
        val pay = pay(start = friday)
        assertEquals(friday, Schedule.nextOnOrAfter(pay, friday))
        assertEquals(LocalDate.of(2026, 9, 25), Schedule.nextOnOrAfter(pay, LocalDate.of(2026, 9, 12)))
        assertEquals(LocalDate.of(2026, 9, 25), Schedule.nextOnOrAfter(pay, LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun weeklyStopsAtEndDate() {
        val bill = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.WEEKLY,
            start = LocalDate.of(2026, 9, 1),
            end = LocalDate.of(2026, 9, 8),
            amount = 10_00,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8)),
            Schedule.occurrencesBetween(bill, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        )
        assertNull(Schedule.nextOnOrAfter(bill, LocalDate.of(2026, 9, 9)))
    }

    @Test
    fun monthlyClampsShortMonthsAndRestoresAnchorDay() {
        val mortgage = event(
            kind = EventKind.MORTGAGE,
            recurrence = RecurrenceType.MONTHLY,
            start = LocalDate.of(2026, 1, 31),
            amount = 1_800_00,
        )
        assertEquals(LocalDate.of(2026, 2, 28), Schedule.nextOnOrAfter(mortgage, LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), Schedule.nextOnOrAfter(mortgage, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun customIntervals() {
        val days = event(
            recurrence = RecurrenceType.CUSTOM,
            unit = CustomUnit.DAYS,
            interval = 10,
            start = LocalDate.of(2026, 1, 1),
            kind = EventKind.BILL,
        )
        assertEquals(LocalDate.of(2026, 1, 21), Schedule.nextOnOrAfter(days, LocalDate.of(2026, 1, 15)))

        val weeks = event(
            recurrence = RecurrenceType.CUSTOM,
            unit = CustomUnit.WEEKS,
            interval = 3,
            start = LocalDate.of(2026, 1, 1),
            kind = EventKind.BILL,
        )
        assertEquals(LocalDate.of(2026, 1, 22), Schedule.nextOnOrAfter(weeks, LocalDate.of(2026, 1, 8)))

        val months = event(
            recurrence = RecurrenceType.CUSTOM,
            unit = CustomUnit.MONTHS,
            interval = 2,
            start = LocalDate.of(2026, 1, 15),
            kind = EventKind.BILL,
        )
        assertEquals(LocalDate.of(2026, 5, 15), Schedule.nextOnOrAfter(months, LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun homeTotalIncludesBillsMortgageAndCardInsideWindow() {
        val today = LocalDate.of(2026, 9, 10)
        val events = listOf(
            pay(start = LocalDate.of(2026, 9, 4)),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.WEEKLY,
                start = LocalDate.of(2026, 9, 11),
                amount = 40_00,
                name = "Groceries",
            ),
            event(
                kind = EventKind.MORTGAGE,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 9, 15),
                amount = 1_500_00,
                name = "Mortgage",
            ),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 10, 1),
                amount = 80_00,
                name = "Later",
            ),
        )
        val card = card(
            statementDay = 5,
            amount = 200_00,
            captured = LocalDate.of(2026, 9, 5),
            daysAfter = 12,
        )
        val snapshot = BudgetCalculator.calculate(events, listOf(card), today)
        assertEquals(LocalDate.of(2026, 9, 18), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 2), snapshot.followingPayday)
        // Until payday: weekly Sep 11 and Sep 18, mortgage Sep 15, card due Sep 17.
        assertEquals(80_00 + 1_500_00 + 200_00, snapshot.untilPaydayCents)
        assertEquals(4, snapshot.untilPaydayCount)
        // Next period: weekly Sep 25 and Oct 2, plus the Oct 1 bill.
        assertEquals(80_00 + 80_00, snapshot.nextPeriodCents)
        assertEquals(3, snapshot.nextPeriodCount)
    }

    @Test
    fun cardDueAfterPaydayIsExcludedAndOverdueIsIncluded() {
        val today = LocalDate.of(2026, 9, 20)
        val events = listOf(pay(start = LocalDate.of(2026, 9, 25)))
        val later = card(
            statementDay = 20,
            amount = 50_00,
            captured = LocalDate.of(2026, 9, 20),
            daysAfter = 10,
            name = "Later",
        )
        val overdue = card(
            id = 2,
            statementDay = 1,
            amount = 75_00,
            captured = LocalDate.of(2026, 9, 1),
            daysAfter = 5,
            name = "Overdue",
        )
        val beyond = card(
            id = 3,
            statementDay = 15,
            amount = 90_00,
            captured = LocalDate.of(2026, 10, 15),
            daysAfter = 0,
            name = "Beyond",
        )
        val snapshot = BudgetCalculator.calculate(events, listOf(later, overdue, beyond), today)
        assertEquals(LocalDate.of(2026, 9, 25), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 9), snapshot.followingPayday)
        assertEquals(75_00, snapshot.untilPaydayCents)
        assertEquals(1, snapshot.untilPaydayCount)
        assertEquals(50_00, snapshot.nextPeriodCents)
        assertEquals(1, snapshot.nextPeriodCount)
    }

    @Test
    fun todayPaydayLooksThroughTheFollowingPayday() {
        val today = LocalDate.of(2026, 9, 11)
        val events = listOf(
            pay(start = friday),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.WEEKLY,
                start = friday,
                amount = 10_00,
            ),
        )
        val snapshot = BudgetCalculator.calculate(events, emptyList(), today)
        assertEquals(LocalDate.of(2026, 9, 25), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 9), snapshot.followingPayday)
        // Sep 11, 18, and 25 stay in the first window because today does not close it.
        assertEquals(30_00, snapshot.untilPaydayCents)
        assertEquals(3, snapshot.untilPaydayCount)
        // Oct 2 and Oct 9 are the next period.
        assertEquals(20_00, snapshot.nextPeriodCents)
        assertEquals(2, snapshot.nextPeriodCount)
    }

    @Test
    fun paidOccurrenceIsSkippedAndTheNextOneStillCounts() {
        val today = LocalDate.of(2026, 9, 10)
        val bill = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.WEEKLY,
            start = LocalDate.of(2026, 9, 11),
            amount = 40_00,
            name = "Groceries",
        )
        val before = BudgetCalculator.calculate(listOf(pay(start = LocalDate.of(2026, 9, 4)), bill), emptyList(), today)
        assertEquals(80_00, before.untilPaydayCents)
        assertEquals(2, before.untilPaydayCount)
        assertEquals(80_00, before.nextPeriodCents)
        assertEquals(2, before.nextPeriodCount)
        assertEquals(LocalDate.of(2026, 9, 11), BudgetCalculator.nextUnpaidOccurrence(bill, today))

        val paid = bill.copy(paidThroughEpochDay = LocalDate.of(2026, 9, 11).toEpochDay())
        val after = BudgetCalculator.calculate(listOf(pay(start = LocalDate.of(2026, 9, 4)), paid), emptyList(), today)
        assertEquals(40_00, after.untilPaydayCents)
        assertEquals(1, after.untilPaydayCount)
        assertEquals(80_00, after.nextPeriodCents)
        assertEquals(2, after.nextPeriodCount)
        assertEquals(LocalDate.of(2026, 9, 18), BudgetCalculator.nextUnpaidOccurrence(paid, today))
        assertEquals(
            LocalDate.of(2026, 9, 11),
            Schedule.previousStrictlyBefore(paid, LocalDate.of(2026, 9, 18)),
        )
        assertNull(Schedule.previousStrictlyBefore(paid, LocalDate.of(2026, 9, 11)))

        val nextCycle = BudgetCalculator.calculate(
            listOf(pay(start = LocalDate.of(2026, 10, 2)), paid),
            emptyList(),
            LocalDate.of(2026, 10, 1),
        )
        assertEquals(LocalDate.of(2026, 10, 2), nextCycle.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 16), nextCycle.followingPayday)
        assertEquals(40_00, nextCycle.untilPaydayCents)
        assertEquals(1, nextCycle.untilPaydayCount)
        assertEquals(80_00, nextCycle.nextPeriodCents)
        assertEquals(2, nextCycle.nextPeriodCount)
    }

    @Test
    fun payAmountIsIgnoredEvenWhenZero() {
        val today = LocalDate.of(2026, 9, 10)
        val bill = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.WEEKLY,
            start = LocalDate.of(2026, 9, 11),
            amount = 40_00,
        )
        val unpaid = pay(start = LocalDate.of(2026, 9, 4)).copy(amountCents = 0)
        val salaried = unpaid.copy(amountCents = 5_000_00)
        val withoutIncome = BudgetCalculator.calculate(listOf(unpaid, bill), emptyList(), today)
        val withIncome = BudgetCalculator.calculate(listOf(salaried, bill), emptyList(), today)
        assertEquals(LocalDate.of(2026, 9, 18), withoutIncome.nextPayday)
        assertEquals(withoutIncome, withIncome)
        assertEquals(80_00, withoutIncome.untilPaydayCents)
        assertEquals(80_00, withoutIncome.nextPeriodCents)
    }

    @Test
    fun noPayIsAnEmptyHome() {
        val snapshot = BudgetCalculator.calculate(emptyList(), emptyList(), LocalDate.of(2026, 9, 10))
        assertNull(snapshot.nextPayday)
        assertNull(snapshot.followingPayday)
        assertEquals(0, snapshot.untilPaydayCents)
        assertEquals(0, snapshot.nextPeriodCents)
    }

    @Test
    fun noFollowingPaydayLeavesNextPeriodAtZero() {
        val today = LocalDate.of(2026, 9, 10)
        val lastPay = event(
            kind = EventKind.PAY,
            recurrence = RecurrenceType.BIWEEKLY,
            start = LocalDate.of(2026, 9, 18),
            end = LocalDate.of(2026, 9, 18),
            amount = 0,
            name = "Last pay",
        )
        val bill = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.WEEKLY,
            start = LocalDate.of(2026, 9, 18),
            amount = 25_00,
            name = "Weekly",
        )
        val snapshot = BudgetCalculator.calculate(listOf(lastPay, bill), emptyList(), today)
        assertEquals(LocalDate.of(2026, 9, 18), snapshot.nextPayday)
        assertNull(snapshot.followingPayday)
        assertEquals(25_00, snapshot.untilPaydayCents)
        assertEquals(0, snapshot.nextPeriodCents)
        assertEquals(0, snapshot.nextPeriodCount)
    }

    @Test
    fun statementPromptQueuesUncapturedCards() {
        val today = LocalDate.of(2026, 9, 25)
        val captured = card(
            id = 1,
            name = "Captured",
            statementDay = 15,
            captured = LocalDate.of(2026, 9, 15),
        )
        val zeta = card(id = 2, name = "Zeta", statementDay = 20, captured = null)
        val alpha = card(id = 3, name = "Alpha", statementDay = 10, captured = null)
        val prompts = BudgetCalculator.pendingStatements(listOf(captured, zeta, alpha), today)
        assertEquals(listOf("Alpha", "Zeta"), prompts.map { it.cardName })
        assertEquals("2026-09-10", prompts[0].cycleKey)
        assertEquals("2026-09-20", prompts[1].cycleKey)
        assertTrue(BudgetCalculator.pendingStatements(listOf(captured, zeta, alpha), today, setOf(3)).none { it.cardName == "Alpha" })
    }

    @Test
    fun statementDayBeforeTodayUsesThisMonth() {
        val date = Schedule.statementOnOrBefore(15, LocalDate.of(2026, 9, 25))
        assertEquals(LocalDate.of(2026, 9, 15), date)
        val earlier = Schedule.statementOnOrBefore(28, LocalDate.of(2026, 9, 10))
        assertEquals(LocalDate.of(2026, 8, 28), earlier)
    }

    @Test
    fun dueDayOnOrBeforeStatementRollsToNextMonth() {
        val due = Schedule.paymentDueDate(
            statementDay = 15,
            dueMode = DueMode.DAY_OF_MONTH,
            daysAfterStatement = 21,
            dueDay = 10,
            statementDate = LocalDate.of(2026, 9, 15),
        )
        assertEquals(LocalDate.of(2026, 10, 10), due)
    }

    @Test
    fun moneyParsesAndFormatsUsd() {
        assertEquals(240_18L, Money.parse("$240.18"))
        assertEquals(20_00L, Money.parse("20"))
        assertEquals(50L, Money.parse("0.5"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse(""))
        assertEquals("$1,234.56", Money.format(123_456))
        assertEquals("20.50", Money.toInput(20_50))
        assertEquals("20.59", Money.sanitizeInput("20.5abc9"))
    }

    private fun pay(start: LocalDate) = event(
        kind = EventKind.PAY,
        recurrence = RecurrenceType.BIWEEKLY,
        start = start,
        amount = 2_000_00,
        name = "Pay",
    )

    private fun event(
        kind: EventKind,
        recurrence: RecurrenceType,
        start: LocalDate,
        amount: Long = 10_00,
        name: String = "Item",
        end: LocalDate? = null,
        unit: CustomUnit = CustomUnit.MONTHS,
        interval: Int = 1,
    ) = CashEventEntity(
        id = name.hashCode().toLong(),
        name = name,
        amountCents = amount,
        kind = kind,
        recurrence = recurrence,
        interval = interval,
        customUnit = unit,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
    )

    private fun card(
        id: Long = 1,
        name: String = "Card",
        statementDay: Int,
        amount: Long = 0,
        captured: LocalDate? = null,
        daysAfter: Int = 21,
    ) = CreditCardEntity(
        id = id,
        name = name,
        statementDay = statementDay,
        dueMode = DueMode.DAYS_AFTER_STATEMENT,
        daysAfterStatement = daysAfter,
        dueDay = 10,
        amountCents = amount,
        lastCapturedCycleKey = captured?.toString(),
    )
}
