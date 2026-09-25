package com.keenin.calbudget.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.data.db.DueMode
import com.keenin.calbudget.domain.BudgetCalculator
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.domain.Schedule
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.components.EditScaffold
import com.keenin.calbudget.ui.components.EmptyListMessage
import com.keenin.calbudget.ui.components.MenuScaffold
import com.keenin.calbudget.ui.components.MoneyField
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun CardListScreen(
    ui: BudgetUi,
    onOpenMenu: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    MenuScaffold(
        title = "Credit cards",
        onOpenMenu = onOpenMenu,
        onAdd = onAdd,
        addLabel = "Add card",
    ) { padding ->
        if (!ui.loading && ui.cards.isEmpty()) {
            EmptyListMessage(
                title = "No cards yet",
                body = "Add a card with its statement day. The first time you open the app on or after that day, Cal asks for the statement balance.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.cards, key = { it.id }) { card ->
                    CardRow(card = card, ui = ui, onClick = { onOpen(card.id) })
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: CreditCardEntity, ui: BudgetUi, onClick: () -> Unit) {
    val needsStatement = ui.prompts.any { it.cardId == card.id }
    val due = BudgetCalculator.balanceDueDate(card, ui.today)
    val dueLabel = when (card.dueMode) {
        DueMode.DAYS_AFTER_STATEMENT -> "Due ${card.daysAfterStatement} days after statement"
        DueMode.DAY_OF_MONTH -> "Due on day ${card.dueDay}"
    }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Statement day ${card.statementDay} · $dueLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    if (needsStatement) {
                        "Statement needed"
                    } else {
                        "Payment ${due.format(dueFormatter)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (needsStatement) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                Money.format(card.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditScreen(
    cardId: Long?,
    ui: BudgetUi,
    onBack: () -> Unit,
    onSave: (CreditCardEntity, onDone: () -> Unit) -> Unit,
    onDelete: (Long, onDone: () -> Unit) -> Unit,
) {
    val existing = ui.cards.firstOrNull { it.id == cardId }
    var name by rememberSaveable { mutableStateOf("") }
    var statementDayText by rememberSaveable { mutableStateOf("15") }
    var dueModeName by rememberSaveable { mutableStateOf(DueMode.DAYS_AFTER_STATEMENT.name) }
    var daysAfterText by rememberSaveable { mutableStateOf("21") }
    var dueDayText by rememberSaveable { mutableStateOf("10") }
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var statementError by rememberSaveable { mutableStateOf<String?>(null) }
    var dueError by rememberSaveable { mutableStateOf<String?>(null) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing?.id, ui.loading) {
        if (!loaded && existing != null) {
            name = existing.name
            statementDayText = existing.statementDay.toString()
            dueModeName = existing.dueMode.name
            daysAfterText = existing.daysAfterStatement.toString()
            dueDayText = existing.dueDay.toString()
            amount = if (existing.amountCents > 0L) Money.toInput(existing.amountCents) else ""
            notes = existing.notes
            loaded = true
        } else if (!loaded && cardId == null) {
            loaded = true
        }
    }

    val missing = !ui.loading && cardId != null && existing == null
    val dueMode = DueMode.valueOf(dueModeName)
    EditScaffold(
        title = if (cardId == null) "New card" else "Edit card",
        onBack = onBack,
    ) { padding ->
        if (missing) {
            EmptyListMessage(
                title = "Not found",
                body = "This card is no longer on the device.",
                modifier = Modifier.padding(padding),
            )
            return@EditScaffold
        }
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "After each statement date, the home screen asks for that cycle’s balance before it shows the number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(80)
                    nameError = null
                },
                label = { Text("Card name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = statementDayText,
                onValueChange = {
                    statementDayText = it.filter { ch -> ch.isDigit() }.take(2)
                    statementError = null
                },
                label = { Text("Statement day") },
                supportingText = { Text(statementError ?: "Day of the month the statement closes, 1–31") },
                isError = statementError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Payment due", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = dueMode == DueMode.DAYS_AFTER_STATEMENT,
                    onClick = { dueModeName = DueMode.DAYS_AFTER_STATEMENT.name },
                    label = { Text("Days after statement") },
                )
                FilterChip(
                    selected = dueMode == DueMode.DAY_OF_MONTH,
                    onClick = { dueModeName = DueMode.DAY_OF_MONTH.name },
                    label = { Text("Day of month") },
                )
            }
            if (dueMode == DueMode.DAYS_AFTER_STATEMENT) {
                OutlinedTextField(
                    value = daysAfterText,
                    onValueChange = {
                        daysAfterText = it.filter { ch -> ch.isDigit() }.take(2)
                        dueError = null
                    },
                    label = { Text("Days after statement") },
                    supportingText = { Text(dueError ?: "Often 21") },
                    isError = dueError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = {
                        dueDayText = it.filter { ch -> ch.isDigit() }.take(2)
                        dueError = null
                    },
                    label = { Text("Due day") },
                    supportingText = {
                        Text(dueError ?: "If this day is on or before the statement day, it’s the next month")
                    },
                    isError = dueError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MoneyField(
                label = "Amount owed",
                value = amount,
                onValueChange = {
                    amount = it
                    amountError = null
                },
                error = amountError,
            )
            Text(
                "Statement balance you still owe. Leave blank until the statement prompt if you don’t know it yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(400) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val statementDay = statementDayText.toIntOrNull()
                    val daysAfter = daysAfterText.toIntOrNull()
                    val dueDay = dueDayText.toIntOrNull()
                    val parsedAmount = if (amount.isBlank()) 0L else Money.parse(amount)
                    var ok = true
                    if (name.isBlank()) {
                        nameError = "Add a name"
                        ok = false
                    }
                    if (statementDay == null || statementDay !in 1..31) {
                        statementError = "Use a day from 1 to 31"
                        ok = false
                    }
                    if (dueMode == DueMode.DAYS_AFTER_STATEMENT && (daysAfter == null || daysAfter !in 0..90)) {
                        dueError = "Use 0 to 90 days"
                        ok = false
                    }
                    if (dueMode == DueMode.DAY_OF_MONTH && (dueDay == null || dueDay !in 1..31)) {
                        dueError = "Use a day from 1 to 31"
                        ok = false
                    }
                    if (parsedAmount == null) {
                        amountError = "Enter a dollar amount, or leave it blank"
                        ok = false
                    }
                    if (!ok || statementDay == null || parsedAmount == null) return@Button
                    val amountChanged = existing != null && parsedAmount != existing.amountCents
                    val cycleKey = when {
                        existing == null -> null
                        amountChanged -> Schedule.statementOnOrBefore(statementDay, ui.today).toString()
                        else -> existing.lastCapturedCycleKey
                    }
                    onSave(
                        CreditCardEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            statementDay = statementDay,
                            dueMode = dueMode,
                            daysAfterStatement = (daysAfter ?: 21).coerceIn(0, 90),
                            dueDay = (dueDay ?: 10).coerceIn(1, 31),
                            amountCents = parsedAmount,
                            notes = notes.trim(),
                            lastCapturedCycleKey = cycleKey,
                        ),
                        onBack,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            if (cardId != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmDelete && cardId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${name.ifBlank { "this card" }}?") },
            text = { Text("The statement balance is removed with the card.") },
            confirmButton = {
                TextButton(onClick = { onDelete(cardId, onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
