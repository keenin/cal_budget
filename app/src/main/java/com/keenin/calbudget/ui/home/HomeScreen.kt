package com.keenin.calbudget.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.cards.StatementCaptureDialog
import java.time.format.DateTimeFormatter
import java.util.Locale

private val paydayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: BudgetUi,
    onOpenMenu: () -> Unit,
    onSetupPay: () -> Unit,
    onCapture: (cardId: Long, amountCents: Long, cycleKey: String) -> Unit,
    onSkipCapture: (Long) -> Unit,
) {
    val prompt = ui.prompts.firstOrNull()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                ui.loading || prompt != null -> Unit
                ui.snapshot.nextPayday == null -> NoPayday(onSetupPay)
                else -> NeededAmount(
                    amount = Money.format(ui.snapshot.amountCents),
                    payday = ui.snapshot.nextPayday.format(paydayFormatter),
                )
            }
        }
    }

    if (prompt != null) {
        StatementCaptureDialog(
            prompt = prompt,
            position = ui.prompts.indexOf(prompt) + 1,
            total = ui.prompts.size,
            initialAmount = ui.cards.firstOrNull { it.id == prompt.cardId }?.amountCents ?: 0L,
            onSave = { cents -> onCapture(prompt.cardId, cents, prompt.cycleKey) },
            onSkip = { onSkipCapture(prompt.cardId) },
        )
    }
}

@Composable
private fun NeededAmount(amount: String, payday: String) {
    val size = when {
        amount.length >= 14 -> 40.sp
        amount.length >= 12 -> 48.sp
        amount.length >= 10 -> 56.sp
        else -> 64.sp
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "NEEDED UNTIL PAYDAY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            amount,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                lineHeight = size * 1.05f,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "by $payday",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoPayday(onSetupPay: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Text(
            "No payday yet",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This screen shows one number: how much you need in the bank until you get paid.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Open the menu at the top right, then choose Pay.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSetupPay) {
            Text("Set up pay")
        }
    }
}
