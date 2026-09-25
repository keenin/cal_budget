package com.keenin.calbudget.domain

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CustomUnit
import com.keenin.calbudget.data.db.DueMode
import com.keenin.calbudget.data.db.RecurrenceType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

object Schedule {
    fun nextOnOrAfter(event: CashEventEntity, from: LocalDate): LocalDate? {
        val start = LocalDate.ofEpochDay(event.startEpochDay)
        val end = event.endEpochDay?.let(LocalDate::ofEpochDay)
        if (end != null && end.isBefore(start)) return null
        val candidate = if (!from.isAfter(start)) {
            start
        } else {
            val dayStep = dayStep(event)
            if (dayStep != null) {
                val between = ChronoUnit.DAYS.between(start, from)
                val steps = (between + dayStep - 1) / dayStep
                start.plusDays(steps * dayStep)
            } else {
                nextMonthly(start, from, monthInterval(event))
            }
        }
        if (end != null && candidate.isAfter(end)) return null
        return candidate
    }

    /** Latest occurrence strictly before [date], if the schedule has one. */
    fun previousStrictlyBefore(event: CashEventEntity, date: LocalDate): LocalDate? {
        var cursor = nextOnOrAfter(event, LocalDate.ofEpochDay(event.startEpochDay)) ?: return null
        var previous: LocalDate? = null
        var guard = 0
        while (cursor.isBefore(date) && guard < 10_000) {
            previous = cursor
            val next = nextOnOrAfter(event, cursor.plusDays(1)) ?: break
            if (!next.isAfter(cursor)) break
            cursor = next
            guard++
        }
        return previous
    }

    fun occurrencesBetween(event: CashEventEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val dates = ArrayList<LocalDate>()
        var cursor = nextOnOrAfter(event, from) ?: return emptyList()
        var guard = 0
        while (!cursor.isAfter(to) && guard < 10_000) {
            dates.add(cursor)
            val next = nextOnOrAfter(event, cursor.plusDays(1)) ?: break
            if (!next.isAfter(cursor)) break
            cursor = next
            guard++
        }
        return dates
    }

    fun statementOnOrBefore(statementDay: Int, today: LocalDate): LocalDate {
        val day = statementDay.coerceIn(1, 31)
        val thisMonth = clampedDay(YearMonth.from(today), day)
        return if (!thisMonth.isAfter(today)) {
            thisMonth
        } else {
            clampedDay(YearMonth.from(today).minusMonths(1), day)
        }
    }

    fun paymentDueDate(
        statementDay: Int,
        dueMode: DueMode,
        daysAfterStatement: Int,
        dueDay: Int,
        statementDate: LocalDate,
    ): LocalDate {
        return when (dueMode) {
            DueMode.DAYS_AFTER_STATEMENT ->
                statementDate.plusDays(daysAfterStatement.coerceIn(0, 90).toLong())
            DueMode.DAY_OF_MONTH -> {
                val configuredStatement = statementDay.coerceIn(1, 31)
                val configuredDue = dueDay.coerceIn(1, 31)
                val month = if (configuredDue > configuredStatement) {
                    YearMonth.from(statementDate)
                } else {
                    YearMonth.from(statementDate).plusMonths(1)
                }
                clampedDay(month, configuredDue)
            }
        }
    }

    fun recurrenceLabel(event: CashEventEntity): String {
        return when (event.recurrence) {
            RecurrenceType.WEEKLY -> "Weekly"
            RecurrenceType.BIWEEKLY -> "Every 2 weeks"
            RecurrenceType.MONTHLY -> "Monthly"
            RecurrenceType.CUSTOM -> customLabel(event.interval, event.customUnit)
        }
    }

    fun customLabel(interval: Int, unit: CustomUnit): String {
        val n = interval.coerceAtLeast(1)
        return when (unit) {
            CustomUnit.DAYS -> if (n == 1) "Daily" else "Every $n days"
            CustomUnit.WEEKS -> if (n == 1) "Weekly" else "Every $n weeks"
            CustomUnit.MONTHS -> if (n == 1) "Monthly" else "Every $n months"
        }
    }

    private fun dayStep(event: CashEventEntity): Long? {
        return when (event.recurrence) {
            RecurrenceType.WEEKLY -> 7L
            RecurrenceType.BIWEEKLY -> 14L
            RecurrenceType.CUSTOM -> when (event.customUnit) {
                CustomUnit.DAYS -> event.interval.coerceAtLeast(1).toLong()
                CustomUnit.WEEKS -> event.interval.coerceAtLeast(1).toLong() * 7L
                CustomUnit.MONTHS -> null
            }
            RecurrenceType.MONTHLY -> null
        }
    }

    private fun monthInterval(event: CashEventEntity): Int {
        return when (event.recurrence) {
            RecurrenceType.MONTHLY -> 1
            RecurrenceType.CUSTOM -> event.interval.coerceAtLeast(1)
            else -> 1
        }
    }

    private fun nextMonthly(start: LocalDate, from: LocalDate, interval: Int): LocalDate {
        val step = interval.coerceAtLeast(1)
        val anchorDay = start.dayOfMonth
        fun occurrence(n: Long): LocalDate {
            val shifted = start.withDayOfMonth(1).plusMonths(n * step)
            return clampedDay(YearMonth.from(shifted), anchorDay)
        }
        val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), from.withDayOfMonth(1))
        var n = (months / step).coerceAtLeast(0)
        var occ = occurrence(n)
        while (n > 0 && !occurrence(n - 1).isBefore(from)) {
            n -= 1
            occ = occurrence(n)
        }
        while (occ.isBefore(from)) {
            n += 1
            occ = occurrence(n)
        }
        return occ
    }

    private fun clampedDay(month: YearMonth, day: Int): LocalDate {
        return month.atDay(min(day, month.lengthOfMonth()))
    }
}
