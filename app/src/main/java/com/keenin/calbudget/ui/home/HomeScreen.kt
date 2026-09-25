package com.keenin.calbudget.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keenin.calbudget.domain.Money
import com.keenin.calbudget.ui.BudgetUi
import com.keenin.calbudget.ui.nav.Routes
import com.keenin.calbudget.ui.cards.StatementCaptureDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ui: BudgetUi,
    onOpenMenu: () -> Unit,
    onCapture: (cardId: Long, amountCents: Long, cycleKey: String) -> Unit,
    onSkipCapture: (Long) -> Unit,
    onOpenBreakdown: (String) -> Unit,
) {
    val prompt = ui.prompts.firstOrNull()
    val untilPayday = Money.format(if (ui.snapshot.nextPayday == null) 0 else ui.snapshot.untilPaydayCents)
    val nextPeriod = Money.format(if (ui.snapshot.followingPayday == null) 0 else ui.snapshot.nextPeriodCents)
    val followingPeriod = Money.format(if (ui.snapshot.thirdPayday == null) 0 else ui.snapshot.followingPeriodCents)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "What I owe",
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
            if (!ui.loading) {
                HomeAmounts(
                    untilPayday = untilPayday,
                    nextPeriod = nextPeriod,
                    followingPeriod = followingPeriod,
                    onUntilPayday = { onOpenBreakdown(Routes.WINDOW_UNTIL) },
                    onNextPeriod = { onOpenBreakdown(Routes.WINDOW_NEXT) },
                    onFollowingPeriod = { onOpenBreakdown(Routes.WINDOW_FOLLOWING) },
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
private fun HomeAmounts(
    untilPayday: String,
    nextPeriod: String,
    followingPeriod: String,
    onUntilPayday: () -> Unit,
    onNextPeriod: () -> Unit,
    onFollowingPeriod: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PeriodAmount(label = "Until payday", amount = untilPayday, primary = true, onClick = onUntilPayday)
        Spacer(Modifier.height(28.dp))
        PeriodAmount(label = "Next period", amount = nextPeriod, primary = false, onClick = onNextPeriod)
        Spacer(Modifier.height(28.dp))
        PeriodAmount(label = "Following period", amount = followingPeriod, primary = false, onClick = onFollowingPeriod)
    }
}

@Composable
private fun PeriodAmount(
    label: String,
    amount: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val size = amountSize(amount, primary)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = "$label, $amount" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (primary) 8.dp else 4.dp))
        Text(
            amount,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                lineHeight = size * 1.05f,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun amountSize(amount: String, primary: Boolean) = when {
    amount.length >= 14 -> if (primary) 36.sp else 28.sp
    amount.length >= 12 -> if (primary) 44.sp else 32.sp
    amount.length >= 10 -> if (primary) 52.sp else 36.sp
    else -> if (primary) 64.sp else 40.sp
}
