package com.keenin.calbudget.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class EventKind {
    BILL,
    MORTGAGE,
    PAY,
}

enum class RecurrenceType {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    CUSTOM,
}

enum class CustomUnit {
    DAYS,
    WEEKS,
    MONTHS,
}

enum class DueMode {
    DAYS_AFTER_STATEMENT,
    DAY_OF_MONTH,
}

@Entity(tableName = "cash_events")
data class CashEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val kind: EventKind,
    val recurrence: RecurrenceType,
    val interval: Int = 1,
    val customUnit: CustomUnit = CustomUnit.MONTHS,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val notes: String = "",
    /** Occurrences on or before this date are paid and do not count toward the home total. */
    val paidThroughEpochDay: Long? = null,
    /** When true, an occurrence is treated as paid on its due date and drops out of the totals. */
    @ColumnInfo(defaultValue = "0")
    val autoPay: Boolean = false,
)

@Entity(tableName = "credit_cards")
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val statementDay: Int,
    val dueMode: DueMode,
    val daysAfterStatement: Int,
    val dueDay: Int,
    val amountCents: Long,
    val notes: String = "",
    /** ISO date of the statement this balance belongs to, or null if not captured. */
    val lastCapturedCycleKey: String? = null,
    /** Payments already applied to [amountCents] for [lastCapturedCycleKey]. */
    @ColumnInfo(defaultValue = "0")
    val paidTowardCents: Long = 0,
)

class Converters {
    @TypeConverter
    fun eventKindToString(value: EventKind): String = value.name

    @TypeConverter
    fun stringToEventKind(value: String): EventKind = EventKind.valueOf(value)

    @TypeConverter
    fun recurrenceToString(value: RecurrenceType): String = value.name

    @TypeConverter
    fun stringToRecurrence(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun unitToString(value: CustomUnit): String = value.name

    @TypeConverter
    fun stringToUnit(value: String): CustomUnit = CustomUnit.valueOf(value)

    @TypeConverter
    fun dueModeToString(value: DueMode): String = value.name

    @TypeConverter
    fun stringToDueMode(value: String): DueMode = DueMode.valueOf(value)
}
