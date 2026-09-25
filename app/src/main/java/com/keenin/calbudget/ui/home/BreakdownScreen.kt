package com.keenin.calbudget.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.domain.ObligationLine
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.components.EditScaffold
import com.keenin.calbudget.ui.components.EmptyListMessage
import com.keenin.calbudget.ui.nav.Routes
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun BreakdownScreen(
    window: String,
    ui: BudgetUi,
    onBack: () -> Unit,
) {
    val title = when (window) {
        Routes.WINDOW_NEXT -> "Next period"
        Routes.WINDOW_FOLLOWING -> "Following period"
        else -> "Until payday"
    }
    val lines = when (window) {
        Routes.WINDOW_NEXT -> ui.snapshot.nextPeriod
        Routes.WINDOW_FOLLOWING -> ui.snapshot.followingPeriod
        else -> ui.snapshot.untilPayday
    }
    val dates = when (window) {
        Routes.WINDOW_NEXT -> ui.snapshot.nextSpan()
        Routes.WINDOW_FOLLOWING -> ui.snapshot.followingSpan()
        else -> ui.snapshot.untilSpan(ui.today)
    }
    val total = lines.sumOf { it.amountCents }
    EditScaffold(title = title, onBack = onBack) { padding ->
        if (lines.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                TotalHeader(total, dates)
                EmptyListMessage(
                    title = "Nothing due",
                    body = "No unpaid bills, housing, or card balances fall in this window.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TotalHeader(total, dates) }
                itemsIndexed(lines, key = { index, line -> "$index-${line.due.toEpochDay()}-${line.name}" }) { _, line ->
                    ObligationRow(line)
                }
            }
        }
    }
}

@Composable
private fun TotalHeader(total: Long, dates: String?) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        if (dates != null) {
            Text(
                dates,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Money.format(total),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ObligationRow(line: ObligationLine) {
    val whenLabel = if (line.overdue) {
        "Overdue · ${line.due.format(dueFormatter)}"
    } else {
        "Due ${line.due.format(dueFormatter)}"
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    whenLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                Money.format(line.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
