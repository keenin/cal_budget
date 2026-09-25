package com.keenin.calbudget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.ui.components.MenuScaffold
import com.keenin.calbudget.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenMenu: () -> Unit,
    onClearAll: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    MenuScaffold(title = "Settings", onOpenMenu = onOpenMenu) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How the numbers work", style = MaterialTheme.typography.titleLarge)
            Text(
                "Until payday is bills, housing, and card payments due from today through the next payday. Overdue card balances stay in that number. If today is payday, the next payday is the one after today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Next period is what is due after that payday, through the payday after it. Following period is what is due after that, through the third payday. If a later payday does not exist, that number is $0.00.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A marked-paid bill date is left out of all three. A bill or housing payment set to automatic payment drops off on its due date; otherwise it still counts that day. A partial card payment reduces only the unpaid remainder. Paycheck amounts are not part of any number.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Amounts are US dollars. Everything is stored on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Dark mode",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice(ThemeMode.SYSTEM, "System default", themeMode, onThemeMode)
                ThemeChoice(ThemeMode.LIGHT, "Light", themeMode, onThemeMode)
                ThemeChoice(ThemeMode.DARK, "Dark", themeMode, onThemeMode)
            }
            Text("Data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = { confirm = true }) {
                Text("Erase all data")
            }
            Text(
                "Version 1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Erase everything?") },
            text = { Text("Bills, cards, housing, and pay schedules will be deleted from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onClearAll()
                    },
                ) { Text("Erase", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ThemeChoice(
    mode: ThemeMode,
    label: String,
    selected: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onThemeMode(mode) },
        label = { Text(label) },
    )
}
