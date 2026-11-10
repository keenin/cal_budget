package com.keenin.calbudget.data

import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.CustomUnit
import com.keenin.calbudget.data.db.DueMode
import com.keenin.calbudget.data.db.EventKind
import com.keenin.calbudget.data.db.RecurrenceType
import com.keenin.calbudget.domain.BudgetCalculator
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builds one of every stored kind, writes the backup JSON, and asks the web
 * app's own import (node, web/src/js/backup.js) to accept it. Home totals are
 * compared with the Android calculator for the same rows.
 *
 * Automatic-payment dates in this sample are after [today]. The web app drops
 * an automatic payment from its totals on the due date; the Android app keeps
 * that amount until it is marked paid. Dates after today are still counted by
 * both, so this check is about the file, not that difference.
 */
class BackupExportTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val exportedAt = "2026-11-10T15:04:05.000Z"

    @Test
    fun exportMatchesWebImportAndTotals() {
        val events = sampleEvents()
        val cards = sampleCards()
        val json = BackupJson.serialize(events, cards, theme = "DARK", exportedAt = exportedAt)

        assertEquals("calbudget-backup-2026-11-10.json", BackupJson.suggestedFileName(today))
        assertFalse(Regex(""""(amountCents|paidTowardCents)":-?\d+\.""").containsMatchIn(json))
        assertTrue(json.contains("\"app\":\"calbudget\""))
        assertTrue(json.contains("\"version\":1"))
        assertTrue(json.contains("\"theme\":\"DARK\""))
        val payday = events.single { it.kind == EventKind.PAY }
        assertTrue(payday.autoPay)
        assertTrue(json.contains("\"name\":\"Payday\""))

        val snapshot = BudgetCalculator.calculate(events, cards, today)
        assertEquals(
            snapshot.untilPayday.joinToString { "${it.name} ${it.due} ${it.amountCents}" },
            16_640L,
            snapshot.untilPaydayCents,
        )
        assertEquals(4, snapshot.untilPaydayCount)
        assertEquals(
            snapshot.nextPeriod.joinToString { "${it.name} ${it.due} ${it.amountCents}" },
            42_317L,
            snapshot.nextPeriodCents,
        )
        assertEquals(8, snapshot.nextPeriodCount)
        assertEquals(
            snapshot.followingPeriod.joinToString { "${it.name} ${it.due} ${it.amountCents}" },
            167_599L,
            snapshot.followingPeriodCents,
        )
        assertEquals(6, snapshot.followingPeriodCount)

        val normalized = events.map { event ->
            if (event.kind == EventKind.PAY) event.copy(autoPay = false) else event
        }
        val normalizedJson = BackupJson.serialize(normalized, cards, theme = "DARK", exportedAt = exportedAt)
        assertNotEquals(json, normalizedJson)

        val imported = runWebImport(json)
        assertEquals("DARK", imported.theme)
        assertEquals(5, imported.bills)
        assertEquals(2, imported.housing)
        assertEquals(1, imported.pays)
        assertEquals(5, imported.cards)
        assertEquals(snapshot.untilPaydayCents, imported.untilPaydayCents)
        assertEquals(snapshot.nextPeriodCents, imported.nextPeriodCents)
        assertEquals(snapshot.followingPeriodCents, imported.followingPeriodCents)
        assertEquals(normalizedJson, imported.reserialized)
    }

    private fun runWebImport(json: String): WebImport {
        val web = webDir()
        val dir = File(System.getProperty("java.io.tmpdir"), "calbudget-export-test")
        dir.mkdirs()
        val backup = File(dir, "backup.json")
        val script = File(dir, "accept-import.mjs")
        backup.writeText(json, Charsets.UTF_8)
        script.writeText(nodeScript(web), Charsets.UTF_8)
        val process = ProcessBuilder("node", script.absolutePath, backup.absolutePath, today.toString())
            .directory(web)
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        assertTrue("node did not finish\n$stderr", finished)
        assertEquals(stderr + stdout, 0, process.exitValue())
        val totals = Regex("""TOTALS (\d+) (\d+) (\d+)""").find(stdout)
            ?: error("missing totals\n$stdout")
        val theme = Regex("""THEME (SYSTEM|LIGHT|DARK|null)""").find(stdout)
            ?: error("missing theme\n$stdout")
        val summary = Regex("""SUMMARY (\d+) (\d+) (\d+) (\d+)""").find(stdout)
            ?: error("missing summary\n$stdout")
        val reserialized = stdout.lineSequence().firstOrNull { it.startsWith("JSON ") }?.removePrefix("JSON ")
            ?: error("missing reserialized backup\n$stdout")
        return WebImport(
            theme = theme.groupValues[1],
            bills = summary.groupValues[1].toInt(),
            housing = summary.groupValues[2].toInt(),
            pays = summary.groupValues[3].toInt(),
            cards = summary.groupValues[4].toInt(),
            untilPaydayCents = totals.groupValues[1].toLong(),
            nextPeriodCents = totals.groupValues[2].toLong(),
            followingPeriodCents = totals.groupValues[3].toLong(),
            reserialized = reserialized,
        )
    }

    private fun nodeScript(web: File): String {
        val backupJs = File(web, "src/js/backup.js").toURI()
        val calculatorJs = File(web, "src/js/calculator.js").toURI()
        return """
            import { readFileSync } from 'node:fs';
            import { parseBackup, serializeBackup } from '$backupJs';
            import { calculate } from '$calculatorJs';

            const text = readFileSync(process.argv[2], 'utf8');
            const today = process.argv[3];
            const parsed = parseBackup(text);
            if (!parsed.ok) {
              console.error(parsed.error);
              process.exit(1);
            }
            const snap = calculate(parsed.data.events, parsed.data.cards, today);
            const again = serializeBackup({
              events: parsed.data.events,
              cards: parsed.data.cards,
              theme: parsed.data.theme,
              exportedAt: JSON.parse(text).exportedAt,
            });
            process.stdout.write(`TOTALS ${'$'}{snap.untilPaydayCents} ${'$'}{snap.nextPeriodCents} ${'$'}{snap.followingPeriodCents}\n`);
            process.stdout.write(`THEME ${'$'}{parsed.data.theme}\n`);
            process.stdout.write(`SUMMARY ${'$'}{parsed.summary.bills} ${'$'}{parsed.summary.housing} ${'$'}{parsed.summary.pays} ${'$'}{parsed.summary.cards}\n`);
            process.stdout.write(`JSON ${'$'}{again}\n`);
        """.trimIndent() + "\n"
    }

    private fun webDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (true) {
            if (File(dir, "web/src/js/backup.js").isFile) return File(dir, "web")
            dir = dir.parentFile ?: error("Could not find web/src/js/backup.js")
        }
    }

    private fun sampleEvents(): List<CashEventEntity> {
        return listOf(
            event(
                id = 1,
                name = "Electric",
                amount = 8640,
                kind = EventKind.BILL,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 10, 12),
                paidThrough = LocalDate.of(2026, 10, 12),
            ),
            event(
                id = 2,
                name = "Water",
                amount = 1250,
                kind = EventKind.BILL,
                recurrence = RecurrenceType.WEEKLY,
                unit = CustomUnit.WEEKS,
                start = LocalDate.of(2026, 11, 3),
                notes = "city",
            ),
            event(
                id = 3,
                name = "Internet",
                amount = 4599,
                kind = EventKind.BILL,
                recurrence = RecurrenceType.CUSTOM,
                interval = 10,
                unit = CustomUnit.DAYS,
                start = LocalDate.of(2026, 11, 20),
                end = LocalDate.of(2026, 12, 5),
                notes = "promo \"fiber\"\nsecond line",
                autoPay = true,
            ),
            event(
                id = 4,
                name = "Lawn",
                amount = 3000,
                kind = EventKind.BILL,
                recurrence = RecurrenceType.CUSTOM,
                interval = 3,
                unit = CustomUnit.WEEKS,
                start = LocalDate.of(2026, 11, 18),
            ),
            event(
                id = 5,
                name = "Insurance",
                amount = 10_000,
                kind = EventKind.BILL,
                recurrence = RecurrenceType.CUSTOM,
                interval = 6,
                unit = CustomUnit.MONTHS,
                start = LocalDate.of(2027, 1, 15),
                notes = "outside the three windows",
            ),
            event(
                id = 6,
                name = "Rent",
                amount = 150_000,
                kind = EventKind.MORTGAGE,
                recurrence = RecurrenceType.MONTHLY,
                start = LocalDate.of(2026, 11, 1),
                paidThrough = LocalDate.of(2026, 11, 1),
                notes = "unit 4\\rear",
            ),
            event(
                id = 7,
                name = "HOA",
                amount = 7500,
                kind = EventKind.MORTGAGE,
                recurrence = RecurrenceType.BIWEEKLY,
                unit = CustomUnit.WEEKS,
                start = LocalDate.of(2026, 11, 20),
                autoPay = true,
            ),
            event(
                id = 8,
                name = "Payday",
                amount = 0,
                kind = EventKind.PAY,
                recurrence = RecurrenceType.BIWEEKLY,
                unit = CustomUnit.WEEKS,
                start = LocalDate.of(2026, 10, 30),
                notes = "varies",
                autoPay = true,
            ),
        )
    }

    private fun sampleCards(): List<CreditCardEntity> {
        return listOf(
            card(
                id = 11,
                name = "Chase",
                statementDay = 12,
                dueMode = DueMode.DAY_OF_MONTH,
                daysAfter = 21,
                dueDay = 28,
                amount = 8000,
                paidToward = 2500,
                cycle = "2026-10-12",
                notes = "partial",
            ),
            card(
                id = 12,
                name = "Visa",
                statementDay = 1,
                dueMode = DueMode.DAYS_AFTER_STATEMENT,
                daysAfter = 21,
                dueDay = 10,
                amount = 24018,
                paidToward = 5000,
                cycle = "2026-11-01",
                autoPay = true,
            ),
            card(
                id = 13,
                name = "Discover",
                statementDay = 1,
                dueMode = DueMode.DAYS_AFTER_STATEMENT,
                daysAfter = 25,
                dueDay = 10,
                amount = 4200,
                cycle = "2026-11-01",
                notes = "rewards",
                autoPay = true,
            ),
            card(
                id = 14,
                name = "Store",
                statementDay = 5,
                dueMode = DueMode.DAYS_AFTER_STATEMENT,
                daysAfter = 21,
                dueDay = 15,
                amount = 3000,
                paidToward = 3000,
                cycle = "2026-11-05",
                autoPay = true,
            ),
            card(
                id = 15,
                name = "Gas",
                statementDay = 8,
                dueMode = DueMode.DAY_OF_MONTH,
                daysAfter = 0,
                dueDay = 20,
                amount = 1500,
                cycle = null,
            ),
        )
    }

    private fun event(
        id: Long,
        name: String,
        amount: Long,
        kind: EventKind,
        recurrence: RecurrenceType,
        start: LocalDate,
        end: LocalDate? = null,
        paidThrough: LocalDate? = null,
        unit: CustomUnit = CustomUnit.MONTHS,
        interval: Int = 1,
        notes: String = "",
        autoPay: Boolean = false,
    ) = CashEventEntity(
        id = id,
        name = name,
        amountCents = amount,
        kind = kind,
        recurrence = recurrence,
        interval = interval,
        customUnit = unit,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
        notes = notes,
        paidThroughEpochDay = paidThrough?.toEpochDay(),
        autoPay = autoPay,
    )

    private fun card(
        id: Long,
        name: String,
        statementDay: Int,
        dueMode: DueMode,
        daysAfter: Int,
        dueDay: Int,
        amount: Long,
        paidToward: Long = 0,
        cycle: String?,
        notes: String = "",
        autoPay: Boolean = false,
    ) = CreditCardEntity(
        id = id,
        name = name,
        statementDay = statementDay,
        dueMode = dueMode,
        daysAfterStatement = daysAfter,
        dueDay = dueDay,
        amountCents = amount,
        notes = notes,
        lastCapturedCycleKey = cycle,
        paidTowardCents = paidToward,
        autoPay = autoPay,
    )
}

private data class WebImport(
    val theme: String,
    val bills: Int,
    val housing: Int,
    val pays: Int,
    val cards: Int,
    val untilPaydayCents: Long,
    val nextPeriodCents: Long,
    val followingPeriodCents: Long,
    val reserialized: String,
)
