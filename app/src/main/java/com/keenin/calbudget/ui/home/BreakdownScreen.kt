package com.keenin.calbudget.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.domain.BudgetCalculator
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.domain.ObligationLine
import com.keenin.calbudget.domain.ObligationSource
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.components.EditScaffold
import com.keenin.calbudget.ui.components.EmptyListMessage
import com.keenin.calbudget.ui.components.MoneyField
import com.keenin.calbudget.ui.nav.Routes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun BreakdownScreen(
    window: String,
    ui: BudgetUi,
    onBack: () -> Unit,
    onMarkPaid: (Long) -> Unit,
    onUndoPaid: (Long) -> Unit,
    onRecordPayment: (Long, Long) -> Unit,
    onClearPayment: (Long) -> Unit,
    onEdit: (ObligationLine) -> Unit,
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
    var sheetSource by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetId by rememberSaveable { mutableLongStateOf(-1L) }
    var sheetDue by rememberSaveable { mutableLongStateOf(-1L) }
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
                itemsIndexed(lines, key = { index, line -> "$index-${line.source}-${line.sourceId}-${line.due.toEpochDay()}" }) { _, line ->
                    ObligationRow(line) {
                        sheetSource = line.source.name
                        sheetId = line.sourceId
                        sheetDue = line.due.toEpochDay()
                    }
                }
            }
        }
    }
    val source = sheetSource?.let { runCatching { ObligationSource.valueOf(it) }.getOrNull() }
    if (source != null && sheetId >= 0L && sheetDue >= 0L) {
        PaySheet(
            source = source,
            sourceId = sheetId,
            due = LocalDate.ofEpochDay(sheetDue),
            ui = ui,
            onDismiss = { sheetSource = null },
            onMarkPaid = onMarkPaid,
            onUndoPaid = onUndoPaid,
            onRecordPayment = onRecordPayment,
            onClearPayment = onClearPayment,
            onEdit = { line ->
                sheetSource = null
                onEdit(line)
            },
        )
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
private fun ObligationRow(line: ObligationLine, onClick: () -> Unit) {
    val whenLabel = if (line.overdue) {
        "Overdue · ${line.due.format(dueFormatter)}"
    } else {
        "Due ${line.due.format(dueFormatter)}"
    }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(
    source: ObligationSource,
    sourceId: Long,
    due: LocalDate,
    ui: BudgetUi,
    onDismiss: () -> Unit,
    onMarkPaid: (Long) -> Unit,
    onUndoPaid: (Long) -> Unit,
    onRecordPayment: (Long, Long) -> Unit,
    onClearPayment: (Long) -> Unit,
    onEdit: (ObligationLine) -> Unit,
) {
    val event = ui.events.firstOrNull { it.id == sourceId }
    val card = ui.cards.firstOrNull { it.id == sourceId }
    val name = when (source) {
        ObligationSource.CARD -> card?.name
        else -> event?.name
    }
    val amount = when (source) {
        ObligationSource.CARD -> card?.let(BudgetCalculator::remainingOwed)
        else -> event?.amountCents
    }
    val editLine = ObligationLine(
        name = name ?: "Item",
        due = due,
        amountCents = amount ?: 0L,
        overdue = due.isBefore(ui.today),
        sourceId = sourceId,
        source = source,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(name ?: "Not found", style = MaterialTheme.typography.titleLarge)
            Text(
                "Due ${due.format(dueFormatter)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (amount != null) {
                Text(
                    Money.format(amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            when (source) {
                ObligationSource.CARD -> {
                    if (card == null) {
                        MissingItem()
                    } else {
                        CardPayActions(
                            card = card,
                            onRecordPayment = { cents -> onRecordPayment(card.id, cents) },
                            onClearPayment = { onClearPayment(card.id) },
                        )
                    }
                }
                else -> {
                    if (event == null) {
                        MissingItem()
                    } else {
                        val choices = BudgetCalculator.billPayChoices(event, due, ui.today)
                        if (choices.markOn != null && choices.markOn != due) {
                            Text(
                                "Marks ${choices.markOn.format(dueFormatter)}, the next unpaid date.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (choices.markOn != null) {
                            Button(
                                onClick = { onMarkPaid(event.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Text("Mark paid")
                            }
                        }
                        if (choices.canUndo) {
                            TextButton(onClick = { onUndoPaid(event.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Undo paid")
                            }
                        }
                    }
                }
            }
            if (name != null) {
                TextButton(onClick = { onEdit(editLine) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit details")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CardPayActions(
    card: CreditCardEntity,
    onRecordPayment: (Long) -> Unit,
    onClearPayment: () -> Unit,
) {
    val remaining = BudgetCalculator.remainingOwed(card)
    var payment by rememberSaveable(card.id, card.paidTowardCents) { mutableStateOf("") }
    var error by rememberSaveable(card.id) { mutableStateOf<String?>(null) }
    if (card.paidTowardCents > 0L) {
        Text(
            if (remaining == 0L) {
                "Paid in full · ${Money.format(card.amountCents)}"
            } else {
                "Paid ${Money.format(card.paidTowardCents)} of ${Money.format(card.amountCents)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (remaining > 0L) {
        MoneyField(
            label = "Payment",
            value = payment,
            onValueChange = {
                payment = it
                error = null
            },
            error = error,
        )
        Button(
            onClick = {
                val parsed = Money.parse(payment)
                if (parsed == null || parsed <= 0L) {
                    error = "Enter a payment amount"
                    return@Button
                }
                onRecordPayment(parsed)
                payment = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Record payment")
        }
    }
    if (card.paidTowardCents > 0L) {
        TextButton(onClick = onClearPayment, modifier = Modifier.fillMaxWidth()) {
            Text("Clear payment")
        }
    }
}

@Composable
private fun MissingItem() {
    Text(
        "This item is no longer on the device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
