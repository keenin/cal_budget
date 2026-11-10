package com.keenin.calbudget.data

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * JSON backup in the shape accepted by web/src/js/backup.js (version 1).
 *
 * Amounts are integer cents. Calling this only reads the lists it is given.
 */
object BackupJson {
    const val APP = "calbudget"
    const val VERSION = 1

    private val themes = setOf("SYSTEM", "LIGHT", "DARK")

    fun suggestedFileName(today: LocalDate): String = "calbudget-backup-$today.json"

    fun timestamp(now: Instant = Instant.now()): String {
        return DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.MILLIS))
    }

    fun serialize(
        events: List<CashEventEntity>,
        cards: List<CreditCardEntity>,
        theme: String,
        exportedAt: String,
    ): String {
        val safeTheme = if (theme in themes) theme else "SYSTEM"
        return buildString {
            append("{\"app\":")
            append(jsonString(APP))
            append(",\"version\":")
            append(VERSION)
            append(",\"exportedAt\":")
            append(jsonString(exportedAt))
            append(",\"events\":[")
            events.forEachIndexed { index, event ->
                if (index > 0) append(',')
                appendEvent(event)
            }
            append("],\"cards\":[")
            cards.forEachIndexed { index, card ->
                if (index > 0) append(',')
                appendCard(card)
            }
            append("],\"settings\":{\"theme\":")
            append(jsonString(safeTheme))
            append("}}")
        }
    }

    private fun StringBuilder.appendEvent(event: CashEventEntity) {
        append("{\"id\":")
        append(event.id)
        append(",\"name\":")
        append(jsonString(event.name))
        append(",\"amountCents\":")
        append(event.amountCents)
        append(",\"kind\":")
        append(jsonString(event.kind.name))
        append(",\"recurrence\":")
        append(jsonString(event.recurrence.name))
        append(",\"interval\":")
        append(event.interval)
        append(",\"customUnit\":")
        append(jsonString(event.customUnit.name))
        append(",\"startEpochDay\":")
        append(event.startEpochDay)
        append(",\"endEpochDay\":")
        append(jsonLong(event.endEpochDay))
        append(",\"notes\":")
        append(jsonString(event.notes))
        append(",\"paidThroughEpochDay\":")
        append(jsonLong(event.paidThroughEpochDay))
        append(",\"autoPay\":")
        append(jsonBool(event.autoPay))
        append('}')
    }

    private fun StringBuilder.appendCard(card: CreditCardEntity) {
        append("{\"id\":")
        append(card.id)
        append(",\"name\":")
        append(jsonString(card.name))
        append(",\"statementDay\":")
        append(card.statementDay)
        append(",\"dueMode\":")
        append(jsonString(card.dueMode.name))
        append(",\"daysAfterStatement\":")
        append(card.daysAfterStatement)
        append(",\"dueDay\":")
        append(card.dueDay)
        append(",\"amountCents\":")
        append(card.amountCents)
        append(",\"notes\":")
        append(jsonString(card.notes))
        append(",\"lastCapturedCycleKey\":")
        append(jsonStringOrNull(card.lastCapturedCycleKey))
        append(",\"paidTowardCents\":")
        append(card.paidTowardCents)
        append(",\"autoPay\":")
        append(jsonBool(card.autoPay))
        append('}')
    }
}

private fun jsonBool(value: Boolean): String = if (value) "true" else "false"

private fun jsonLong(value: Long?): String = value?.toString() ?: "null"

private fun jsonStringOrNull(value: String?): String {
    return if (value == null) "null" else jsonString(value)
}

/** Matches JSON.stringify for the characters a name or note can contain. */
private fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch.code < 0x20) {
                out.append("\\u")
                out.append(ch.code.toString(16).padStart(4, '0'))
            } else {
                out.append(ch)
            }
        }
    }
    out.append('"')
    return out.toString()
}
