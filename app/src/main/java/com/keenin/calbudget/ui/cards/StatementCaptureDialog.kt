package com.keenin.calbudget.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.domain.StatementPrompt
import java.time.format.DateTimeFormatter
import java.util.Locale

private val statementDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

@Composable
fun StatementCaptureDialog(
    prompt: StatementPrompt,
    position: Int,
    total: Int,
    initialAmount: Long,
    onSave: (Long) -> Unit,
    onSkip: () -> Unit,
) {
    var amount by remember(prompt.cardId) {
        mutableStateOf(if (initialAmount > 0L) Money.toInput(initialAmount) else "")
    }
    var error by remember(prompt.cardId) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("New statement", style = MaterialTheme.typography.headlineMedium)
                if (total > 1) {
                    Text(
                        "Card $position of $total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(prompt.cardName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Statement date · ${prompt.statementDate.format(statementDateFormatter)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Payment due · ${prompt.dueDate.format(statementDateFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter the statement balance. Use 0 if nothing is owed. It counts toward what you need until payday when the payment falls in that window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = Money.sanitizeInput(it)
                        error = null
                    },
                    label = { Text("Statement balance") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val cents = Money.parse(amount)
                        if (cents == null) {
                            error = "Enter a dollar amount, like 240.18"
                        } else {
                            onSave(cents)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save balance")
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Skip for now")
                }
            }
            LaunchedEffect(prompt.cardId) {
                delay(150)
                runCatching { focus.requestFocus() }
            }
        }
    }
}
