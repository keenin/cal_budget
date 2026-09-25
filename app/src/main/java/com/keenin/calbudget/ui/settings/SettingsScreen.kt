package com.keenin.calbudget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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

@Composable
fun SettingsScreen(
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
            Text("How the number works", style = MaterialTheme.typography.titleLarge)
            Text(
                "The home screen adds every bill and housing payment that comes due between today and your next payday, including the payday itself. Credit card statement balances are included when that payment is due by payday, and overdue card balances stay in the total until you change them.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A pay schedule only marks when you get paid. The number is bills, housing, and card payments — not income. If today is payday, the window runs through the following payday.",
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
                "Cal follows the system light and dark theme.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
