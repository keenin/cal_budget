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
        assertEquals(
            listOf(
                line("Groceries", LocalDate.of(2026, 9, 11), 40_00),
                line("Mortgage", LocalDate.of(2026, 9, 15), 1_500_00),
                line("Card", LocalDate.of(2026, 9, 17), 200_00),
                line("Groceries", LocalDate.of(2026, 9, 18), 40_00),
            ),
            snapshot.untilPayday,
        )
        assertEquals(
            listOf(
                line("Groceries", LocalDate.of(2026, 9, 25), 40_00),
                line("Later", LocalDate.of(2026, 10, 1), 80_00),
                line("Groceries", LocalDate.of(2026, 10, 2), 40_00),
            ),
            snapshot.nextPeriod,
        )
        assertEquals(LocalDate.of(2026, 10, 16), snapshot.thirdPayday)
        // Following period: weekly Oct 9 and Oct 16, plus the Oct 15 mortgage.
        assertEquals(
            listOf(
                line("Groceries", LocalDate.of(2026, 10, 9), 40_00),
                line("Mortgage", LocalDate.of(2026, 10, 15), 1_500_00),
                line("Groceries", LocalDate.of(2026, 10, 16), 40_00),
            ),
            snapshot.followingPeriod,
        )
        assertEquals(snapshot.untilPaydayCents, snapshot.untilPayday.sumOf { it.amountCents })
        assertEquals(snapshot.nextPeriodCents, snapshot.nextPeriod.sumOf { it.amountCents })
        assertEquals(snapshot.followingPeriodCents, snapshot.followingPeriod.sumOf { it.amountCents })
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
        assertEquals(LocalDate.of(2026, 10, 23), snapshot.thirdPayday)
        assertEquals(listOf(line("Overdue", LocalDate.of(2026, 9, 6), 75_00, overdue = true)), snapshot.untilPayday)
        assertEquals(listOf(line("Later", LocalDate.of(2026, 9, 30), 50_00)), snapshot.nextPeriod)
        assertEquals(listOf(line("Beyond", LocalDate.of(2026, 10, 15), 90_00)), snapshot.followingPeriod)
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
        assertEquals(LocalDate.of(2026, 10, 23), snapshot.thirdPayday)
        assertEquals(
            listOf(
                line("Item", LocalDate.of(2026, 10, 16), 10_00),
                line("Item", LocalDate.of(2026, 10, 23), 10_00),
            ),
            snapshot.followingPeriod,
        )
        assertEquals(snapshot.followingPeriodCents, snapshot.followingPeriod.sumOf { it.amountCents })
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
        assertEquals(listOf(line("Groceries", LocalDate.of(2026, 9, 18), 40_00)), after.untilPayday)
        assertEquals(
            listOf(
                line("Groceries", LocalDate.of(2026, 9, 25), 40_00),
                line("Groceries", LocalDate.of(2026, 10, 2), 40_00),
            ),
            after.nextPeriod,
        )
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
        assertTrue(withoutIncome.untilPayday.none { it.name == "Pay" })
        assertTrue(withoutIncome.nextPeriod.none { it.name == "Pay" })
    }

    @Test
    fun noPayIsAnEmptyHome() {
        val snapshot = BudgetCalculator.calculate(emptyList(), emptyList(), LocalDate.of(2026, 9, 10))
        assertNull(snapshot.nextPayday)
        assertNull(snapshot.followingPayday)
        assertNull(snapshot.thirdPayday)
        assertEquals(0, snapshot.untilPaydayCents)
        assertEquals(0, snapshot.nextPeriodCents)
        assertEquals(0, snapshot.followingPeriodCents)
        assertTrue(snapshot.untilPayday.isEmpty())
        assertTrue(snapshot.nextPeriod.isEmpty())
        assertTrue(snapshot.followingPeriod.isEmpty())
    }

    @Test
    fun followingPeriodHoldsOnlyTheThirdWindow() {
        val today = LocalDate.of(2026, 9, 10)
        val events = listOf(
            pay(start = LocalDate.of(2026, 9, 4)),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 10, 1),
                amount = 80_00,
                name = "Next only",
            ),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 10, 9),
                amount = 25_00,
                name = "Third only",
            ),
            event(
                kind = EventKind.BILL,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 10, 20),
                amount = 15_00,
                name = "Too late",
            ),
        )
        val snapshot = BudgetCalculator.calculate(events, emptyList(), today)
        assertEquals(LocalDate.of(2026, 9, 18), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 2), snapshot.followingPayday)
        assertEquals(LocalDate.of(2026, 10, 16), snapshot.thirdPayday)
        assertEquals(listOf(line("Next only", LocalDate.of(2026, 10, 1), 80_00)), snapshot.nextPeriod)
        assertEquals(listOf(line("Third only", LocalDate.of(2026, 10, 9), 25_00)), snapshot.followingPeriod)
        assertEquals(80_00, snapshot.nextPeriodCents)
        assertEquals(25_00, snapshot.followingPeriodCents)
        assertEquals(snapshot.nextPeriodCents, snapshot.nextPeriod.sumOf { it.amountCents })
        assertEquals(snapshot.followingPeriodCents, snapshot.followingPeriod.sumOf { it.amountCents })
        assertTrue(snapshot.untilPayday.isEmpty())
        assertTrue((snapshot.untilPayday + snapshot.nextPeriod + snapshot.followingPeriod).none { it.name == "Too late" })
    }

    @Test
    fun noThirdPaydayLeavesFollowingPeriodEmpty() {
        val today = LocalDate.of(2026, 9, 10)
        val twoPays = event(
            kind = EventKind.PAY,
            recurrence = RecurrenceType.BIWEEKLY,
            start = LocalDate.of(2026, 9, 18),
            end = LocalDate.of(2026, 10, 2),
            amount = 2_000_00,
            name = "Two pays",
        )
        val insideNext = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.MONTHLY,
            start = LocalDate.of(2026, 9, 25),
            amount = 12_00,
            name = "Inside next",
        )
        val afterSecond = event(
            kind = EventKind.BILL,
            recurrence = RecurrenceType.MONTHLY,
            start = LocalDate.of(2026, 10, 9),
            amount = 30_00,
            name = "After second",
        )
        val snapshot = BudgetCalculator.calculate(listOf(twoPays, insideNext, afterSecond), emptyList(), today)
        assertEquals(LocalDate.of(2026, 9, 18), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 2), snapshot.followingPayday)
        assertNull(snapshot.thirdPayday)
        assertEquals(listOf(line("Inside next", LocalDate.of(2026, 9, 25), 12_00)), snapshot.nextPeriod)
        assertTrue(snapshot.followingPeriod.isEmpty())
        assertEquals(0, snapshot.followingPeriodCents)
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
        assertNull(snapshot.thirdPayday)
        assertEquals(25_00, snapshot.untilPaydayCents)
        assertEquals(0, snapshot.nextPeriodCents)
        assertEquals(0, snapshot.nextPeriodCount)
        assertEquals(0, snapshot.followingPeriodCents)
        assertTrue(snapshot.followingPeriod.isEmpty())
    }

    @Test
    fun partialCardPaymentReducesTheWindowItsDueDateFallsIn() {
        val today = LocalDate.of(2026, 9, 10)
        val events = listOf(pay(start = LocalDate.of(2026, 9, 4)))
        val untilCard = card(
            statementDay = 5,
            amount = 1_000_00,
            captured = LocalDate.of(2026, 9, 5),
            daysAfter = 12,
        ).let { BudgetCalculator.recordPayment(it, 500_00) }
        val nextCard = card(
            id = 2,
            statementDay = 1,
            amount = 400_00,
            captured = LocalDate.of(2026, 10, 1),
            daysAfter = 0,
            name = "Next",
        ).let { BudgetCalculator.recordPayment(it, 100_00) }
        val snapshot = BudgetCalculator.calculate(events, listOf(untilCard, nextCard), today)
        assertEquals(LocalDate.of(2026, 9, 18), snapshot.nextPayday)
        assertEquals(LocalDate.of(2026, 10, 2), snapshot.followingPayday)
        assertEquals(500_00, snapshot.untilPaydayCents)
        assertEquals(1, snapshot.untilPaydayCount)
        assertEquals(300_00, snapshot.nextPeriodCents)
        assertEquals(1, snapshot.nextPeriodCount)
        assertEquals(listOf(line("Card", LocalDate.of(2026, 9, 17), 500_00)), snapshot.untilPayday)
        assertEquals(listOf(line("Next", LocalDate.of(2026, 10, 1), 300_00)), snapshot.nextPeriod)
    }

    @Test
    fun partialsThatCoverTheBalanceClearTheCardAndOverpayStaysAtZero() {
        val today = LocalDate.of(2026, 9, 20)
        val events = listOf(pay(start = LocalDate.of(2026, 9, 25)))
        val owed = card(
            statementDay = 1,
            amount = 1_000_00,
            captured = LocalDate.of(2026, 9, 1),
            daysAfter = 5,
            name = "Overdue",
        )
        val half = BudgetCalculator.recordPayment(owed, 400_00)
        val halfAgain = BudgetCalculator.recordPayment(half, 600_00)
        val covered = BudgetCalculator.calculate(events, listOf(halfAgain), today)
        assertEquals(0, BudgetCalculator.remainingOwed(halfAgain))
        assertEquals(0, covered.untilPaydayCents)
        assertEquals(0, covered.untilPaydayCount)
        assertTrue(covered.untilPayday.isEmpty())
        assertTrue(covered.nextPeriod.isEmpty())

        val overpaid = BudgetCalculator.recordPayment(owed, 1_500_00)
        val over = BudgetCalculator.calculate(events, listOf(overpaid), today)
        assertEquals(0, BudgetCalculator.remainingOwed(overpaid))
        assertEquals(0, over.untilPaydayCents)
        assertEquals(0, over.nextPeriodCents)

        val cleared = BudgetCalculator.clearPayment(overpaid)
        val restored = BudgetCalculator.calculate(events, listOf(cleared), today)
        assertEquals(1_000_00, restored.untilPaydayCents)
        assertEquals(1, restored.untilPaydayCount)
    }

    @Test
    fun overdueRemainderStaysInUntilPayday() {
        val today = LocalDate.of(2026, 9, 20)
        val events = listOf(pay(start = LocalDate.of(2026, 9, 25)))
        val overdue = card(
            statementDay = 1,
            amount = 1_000_00,
            captured = LocalDate.of(2026, 9, 1),
            daysAfter = 5,
        ).let { BudgetCalculator.recordPayment(it, 250_00) }
        val snapshot = BudgetCalculator.calculate(events, listOf(overdue), today)
        assertEquals(750_00, snapshot.untilPaydayCents)
        assertEquals(0, snapshot.nextPeriodCents)
        assertEquals(listOf(line("Card", LocalDate.of(2026, 9, 6), 750_00, overdue = true)), snapshot.untilPayday)
        assertTrue(snapshot.nextPeriod.isEmpty())
    }

    @Test
    fun newStatementBalanceDropsPaymentsFromThePreviousCycle() {
        val card = card(
            statementDay = 5,
            amount = 1_000_00,
            captured = LocalDate.of(2026, 9, 5),
        ).let { BudgetCalculator.recordPayment(it, 500_00) }
        val nextCycle = BudgetCalculator.applyStatementBalance(card, 800_00, "2026-10-05")
        assertEquals(800_00, nextCycle.amountCents)
        assertEquals(0, nextCycle.paidTowardCents)
        assertEquals("2026-10-05", nextCycle.lastCapturedCycleKey)
        assertEquals(800_00, BudgetCalculator.remainingOwed(nextCycle))

        val sameCycle = BudgetCalculator.applyStatementBalance(card, 900_00, "2026-09-05")
        assertEquals(500_00, sameCycle.paidTowardCents)
        assertEquals(400_00, BudgetCalculator.remainingOwed(sameCycle))

        assertEquals(0, BudgetCalculator.paidTowardForSave(card, "2026-10-05"))
        assertEquals(500_00, BudgetCalculator.paidTowardForSave(card, "2026-09-05"))
        assertEquals(500_00, BudgetCalculator.recordPayment(card, 0).paidTowardCents)
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

    private fun line(name: String, due: LocalDate, amount: Long, overdue: Boolean = false) =
        ObligationLine(name = name, due = due, amountCents = amount, overdue = overdue)

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
        paidTowardCents = 0,
    )
}
