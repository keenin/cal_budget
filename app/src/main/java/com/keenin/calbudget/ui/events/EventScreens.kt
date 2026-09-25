package com.keenin.calbudget.ui.events

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CustomUnit
import com.keenin.calbudget.data.db.EventKind
import com.keenin.calbudget.data.db.RecurrenceType
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.domain.Schedule
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.components.DateField
import com.keenin.calbudget.ui.components.EditScaffold
import com.keenin.calbudget.ui.components.EmptyListMessage
import com.keenin.calbudget.ui.components.MenuScaffold
import com.keenin.calbudget.ui.components.MoneyField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val listDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private data class KindCopy(
    val title: String,
    val addLabel: String,
    val emptyTitle: String,
    val emptyBody: String,
    val editTitleNew: String,
    val editTitleExisting: String,
    val amountLabel: String,
    val helper: String?,
    val defaultRecurrence: RecurrenceType,
)

private fun copyFor(kind: EventKind): KindCopy = when (kind) {
    EventKind.BILL -> KindCopy(
        title = "Bills",
        addLabel = "Add bill",
        emptyTitle = "No bills yet",
        emptyBody = "Add rent extras, utilities, subscriptions, and anything else that repeats.",
        editTitleNew = "New bill",
        editTitleExisting = "Edit bill",
        amountLabel = "Amount",
        helper = null,
        defaultRecurrence = RecurrenceType.MONTHLY,
    )
    EventKind.MORTGAGE -> KindCopy(
        title = "Mortgage / housing",
        addLabel = "Add payment",
        emptyTitle = "No housing payments",
        emptyBody = "Add a mortgage, rent, or HOA. Use monthly, or a custom schedule if it isn’t.",
        editTitleNew = "New housing payment",
        editTitleExisting = "Edit housing payment",
        amountLabel = "Amount",
        helper = null,
        defaultRecurrence = RecurrenceType.MONTHLY,
    )
    EventKind.PAY -> KindCopy(
        title = "Pay schedule",
        addLabel = "Add schedule",
        emptyTitle = "No pay schedule",
        emptyBody = "Set when you get paid. Weekly, biweekly, and monthly are the usual choices. The next payday sets the home screen.",
        editTitleNew = "New pay schedule",
        editTitleExisting = "Edit pay schedule",
        amountLabel = "Amount",
        helper = "Pick a recent or upcoming payday. Cal uses the dates only, not how much you earn.",
        defaultRecurrence = RecurrenceType.BIWEEKLY,
    )
}

@Composable
fun EventListScreen(
    kind: EventKind,
    ui: BudgetUi,
    onOpenMenu: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val copy = copyFor(kind)
    val events = ui.events.filter { it.kind == kind }
    MenuScaffold(
        title = copy.title,
        onOpenMenu = onOpenMenu,
        onAdd = onAdd,
        addLabel = copy.addLabel,
    ) { padding ->
        if (!ui.loading && events.isEmpty()) {
            EmptyListMessage(
                title = copy.emptyTitle,
                body = copy.emptyBody,
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
                items(events, key = { it.id }) { event ->
                    EventRow(event = event, today = ui.today, onClick = { onOpen(event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CashEventEntity, today: LocalDate, onClick: () -> Unit) {
    val next = Schedule.nextOnOrAfter(event, today)
    val subtitle = buildString {
        append(Schedule.recurrenceLabel(event))
        append(" · ")
        append(if (next == null) "Ended" else "next ${next.format(listDate)}")
    }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (event.kind != EventKind.PAY) {
                Text(
                    Money.format(event.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    kind: EventKind,
    eventId: Long?,
    ui: BudgetUi,
    onBack: () -> Unit,
    onSave: (CashEventEntity, onDone: () -> Unit) -> Unit,
    onDelete: (Long, onDone: () -> Unit) -> Unit,
) {
    val copy = copyFor(kind)
    val existing = ui.events.firstOrNull { it.id == eventId }
    val scheduleOnly = kind == EventKind.PAY
    var name by rememberSaveable { mutableStateOf(if (scheduleOnly) "Payday" else "") }
    var amount by rememberSaveable { mutableStateOf("") }
    var recurrenceName by rememberSaveable { mutableStateOf(copy.defaultRecurrence.name) }
    var unitName by rememberSaveable { mutableStateOf(CustomUnit.MONTHS.name) }
    var intervalText by rememberSaveable { mutableStateOf("2") }
    var startEpoch by rememberSaveable { mutableStateOf(ui.today.toEpochDay()) }
    var ongoing by rememberSaveable { mutableStateOf(true) }
    var endEpoch by rememberSaveable { mutableStateOf(ui.today.plusYears(1).toEpochDay()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var intervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var endError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing?.id, ui.loading) {
        if (!loaded && existing != null) {
            name = existing.name
            amount = Money.toInput(existing.amountCents)
            recurrenceName = existing.recurrence.name
            unitName = existing.customUnit.name
            intervalText = existing.interval.coerceAtLeast(1).toString()
            startEpoch = existing.startEpochDay
            ongoing = existing.endEpochDay == null
            endEpoch = existing.endEpochDay ?: LocalDate.ofEpochDay(existing.startEpochDay).plusYears(1).toEpochDay()
            notes = existing.notes
            loaded = true
        } else if (!loaded && eventId == null) {
            loaded = true
        }
    }

    val missing = !ui.loading && eventId != null && existing == null
    EditScaffold(
        title = if (eventId == null) copy.editTitleNew else copy.editTitleExisting,
        onBack = onBack,
    ) { padding ->
        if (missing) {
            EmptyListMessage(
                title = "Not found",
                body = "This item is no longer on the device.",
                modifier = Modifier.padding(padding),
            )
            return@EditScaffold
        }
        val recurrence = RecurrenceType.valueOf(recurrenceName)
        val unit = CustomUnit.valueOf(unitName)
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (copy.helper != null) {
                Text(
                    copy.helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(80)
                    nameError = null
                },
                label = { Text(if (scheduleOnly) "Schedule name" else "Name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!scheduleOnly) {
                MoneyField(
                    label = copy.amountLabel,
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = null
                    },
                    error = amountError,
                )
            }
            Text("Repeats", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceType.entries.forEach { option ->
                    FilterChip(
                        selected = recurrence == option,
                        onClick = { recurrenceName = option.name },
                        label = {
                            Text(
                                when (option) {
                                    RecurrenceType.WEEKLY -> "Weekly"
                                    RecurrenceType.BIWEEKLY -> "Biweekly"
                                    RecurrenceType.MONTHLY -> "Monthly"
                                    RecurrenceType.CUSTOM -> "Custom"
                                },
                            )
                        },
                    )
                }
            }
            if (recurrence == RecurrenceType.CUSTOM) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = {
                        intervalText = it.filter { ch -> ch.isDigit() }.take(3)
                        intervalError = null
                    },
                    label = { Text("Every") },
                    singleLine = true,
                    isError = intervalError != null,
                    supportingText = intervalError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomUnit.entries.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unitName = option.name },
                            label = {
                                Text(
                                    when (option) {
                                        CustomUnit.DAYS -> "Days"
                                        CustomUnit.WEEKS -> "Weeks"
                                        CustomUnit.MONTHS -> "Months"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    Schedule.customLabel(intervalText.toIntOrNull() ?: 1, unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DateField(
                label = if (scheduleOnly) "Payday date" else "Start date",
                date = LocalDate.ofEpochDay(startEpoch),
                onDateChange = { startEpoch = it.toEpochDay() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ongoing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn off to set an end date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ongoing, onCheckedChange = { ongoing = it })
            }
            if (!ongoing) {
                DateField(
                    label = "End date",
                    date = LocalDate.ofEpochDay(endEpoch),
                    onDateChange = {
                        endEpoch = it.toEpochDay()
                        endError = null
                    },
                )
                if (endError != null) {
                    Text(endError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(400) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val parsed = if (scheduleOnly) 0L else Money.parse(amount)
                    val interval = intervalText.toIntOrNull()
                    var ok = true
                    if (name.isBlank()) {
                        nameError = "Add a name"
                        ok = false
                    }
                    if (!scheduleOnly && (parsed == null || parsed <= 0L)) {
                        amountError = "Enter an amount greater than zero"
                        ok = false
                    }
                    if (recurrence == RecurrenceType.CUSTOM && (interval == null || interval < 1)) {
                        intervalError = "Use a number of 1 or more"
                        ok = false
                    }
                    if (!ongoing && endEpoch < startEpoch) {
                        endError = "End date can’t be before the start date"
                        ok = false
                    }
                    if (!ok || parsed == null) return@Button
                    onSave(
                        CashEventEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            amountCents = parsed,
                            kind = kind,
                            recurrence = recurrence,
                            interval = if (recurrence == RecurrenceType.CUSTOM) interval!!.coerceAtLeast(1) else 1,
                            customUnit = unit,
                            startEpochDay = startEpoch,
                            endEpochDay = if (ongoing) null else endEpoch,
                            notes = notes.trim(),
                        ),
                        onBack,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            if (eventId != null) {
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

    if (confirmDelete && eventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${name.ifBlank { "this item" }}?") },
            text = { Text("This removes it from the device. The home number updates immediately.") },
            confirmButton = {
                TextButton(onClick = { onDelete(eventId, onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
