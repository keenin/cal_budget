package com.keenin.calbudget.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.BuildConfig
import com.keenin.calbudget.data.BackupJson
import com.keenin.calbudget.data.db.CashEventEntity
import com.keenin.calbudget.data.db.CreditCardEntity
import com.keenin.calbudget.ui.components.MenuScaffold
import com.keenin.calbudget.ui.theme.ThemeMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    events: List<CashEventEntity>,
    cards: List<CreditCardEntity>,
    loading: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenMenu: () -> Unit,
    onClearAll: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val createDocument = rememberLauncherForActivityResult(CreateJsonDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = BackupJson.serialize(
            events = events,
            cards = cards,
            theme = themeMode.name,
            exportedAt = BackupJson.timestamp(),
        )
        val appContext = context.applicationContext
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { writeBackup(appContext, uri, json) }.isSuccess
            }
            if (!isActive) return@launch
            exportFailed = !saved
            exportMessage = if (saved) {
                "Backup saved. On the web app, open Settings and choose Import backup."
            } else {
                "Couldn’t save the backup."
            }
        }
    }
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
                "Until payday is unpaid bills, housing, and card payments due through the next payday, including ones whose date has already passed. If today is payday, the next payday is the one after today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Next period is what is due after that payday, through the payday after it. Following period is what is due after that, through the third payday. If a later payday does not exist, that number is $0.00.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A bill or housing date stays in the totals until you mark that occurrence paid, including after its due date. A card remainder stays until you record a payment that covers it, including after its due date. Automatic payment does not clear either one. A partial card payment reduces only what is still unpaid. Each occurrence is counted once. Paycheck amounts are not part of any number.",
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
            OutlinedButton(
                onClick = { createDocument.launch(BackupJson.suggestedFileName(LocalDate.now())) },
                enabled = !loading,
            ) {
                Text("Export data")
            }
            Text(
                "Saves a JSON backup the web app can import from Settings. The phone asks where to put the file, such as Downloads or Drive. Bills, housing, cards, partial payments, paid dates, automatic payment, pay schedules, and appearance are included. Money stays in cents. Nothing already saved in the app is changed or deleted.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exportMessage != null) {
                Text(
                    exportMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (exportFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            OutlinedButton(onClick = { confirm = true }) {
                Text("Erase all data")
            }
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
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

/**
 * Storage Access Framework create flow. The system grants write access to the
 * chosen file, so the app does not request storage permissions.
 */
private class CreateJsonDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

private fun writeBackup(context: Context, uri: Uri, json: String) {
    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("The selected location could not be opened")
    stream.use { output ->
        output.write(json.toByteArray(Charsets.UTF_8))
        output.flush()
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
