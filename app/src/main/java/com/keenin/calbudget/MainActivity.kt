package com.keenin.calbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keenin.calbudget.ui.AppRoot
import com.keenin.calbudget.ui.BudgetViewModel
import com.keenin.calbudget.ui.BudgetViewModelFactory
import com.keenin.calbudget.ui.theme.CalBudgetTheme
import com.keenin.calbudget.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as CalBudgetApp
            val viewModel: BudgetViewModel = viewModel(
                factory = BudgetViewModelFactory(app.repository),
            )
            val themeMode by app.themeSettings.mode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshToday()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            CalBudgetTheme(themeMode) {
                AppRoot(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeMode = { mode -> scope.launch { app.themeSettings.setMode(mode) } },
                )
            }
        }
    }
}
