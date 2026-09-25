package com.keenin.calbudget.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object Money {
    private val inputPattern = Regex("""\d+(\.\d{0,2})?""")

    fun parse(raw: String): Long? {
        val cleaned = raw.trim().replace("$", "").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty() || !inputPattern.matches(cleaned)) return null
        val parts = cleaned.split('.')
        val dollars = parts[0].toLongOrNull() ?: return null
        if (dollars > 100_000_000L) return null
        val cents = if (parts.size == 1 || parts[1].isEmpty()) {
            0
        } else {
            parts[1].padEnd(2, '0').take(2).toInt()
        }
        return dollars * 100 + cents
    }

    fun format(cents: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(BigDecimal.valueOf(cents, 2))
    }

    /** Plain number for a text field that already shows a dollar prefix. */
    fun toInput(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val dollars = abs / 100
        val fraction = (abs % 100).toInt()
        val body = if (fraction == 0) {
            dollars.toString()
        } else {
            "$dollars.${fraction.toString().padStart(2, '0')}"
        }
        return if (negative) "-$body" else body
    }

    fun sanitizeInput(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        val dot = cleaned.indexOf('.')
        if (dot < 0) return cleaned.take(9)
        val whole = cleaned.substring(0, dot).take(9)
        val fraction = cleaned.substring(dot + 1).filter { it.isDigit() }.take(2)
        return "$whole.$fraction"
    }
}
