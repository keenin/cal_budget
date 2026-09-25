package com.keenin.calbudget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keenin.calbudget.data.db.EventKind
import com.keenin.calbudget.ui.cards.CardEditScreen
import com.keenin.calbudget.ui.cards.CardListScreen
import com.keenin.calbudget.ui.components.EndDrawer
import com.keenin.calbudget.ui.events.EventEditScreen
import com.keenin.calbudget.ui.events.EventListScreen
import com.keenin.calbudget.ui.home.HomeScreen
import com.keenin.calbudget.ui.nav.Routes
import com.keenin.calbudget.ui.settings.SettingsScreen

@Composable
fun AppRoot(viewModel: BudgetViewModel) {
    val navController = rememberNavController()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    ui = ui,
                    onOpenMenu = { menuOpen = true },
                    onSetupPay = {
                        navController.navigate(Routes.PAY) { launchSingleTop = true }
                    },
                    onCapture = { cardId, cents, cycleKey ->
                        viewModel.captureStatement(cardId, cents, cycleKey)
                    },
                    onSkipCapture = viewModel::skipStatement,
                )
            }
            composable(Routes.BILLS) {
                EventListScreen(
                    kind = EventKind.BILL,
                    ui = ui,
                    onOpenMenu = { menuOpen = true },
                    onAdd = { navController.navigate(Routes.billEdit(null)) },
                    onOpen = { id -> navController.navigate(Routes.billEdit(id)) },
                )
            }
            composable(Routes.MORTGAGE) {
                EventListScreen(
                    kind = EventKind.MORTGAGE,
                    ui = ui,
                    onOpenMenu = { menuOpen = true },
                    onAdd = { navController.navigate(Routes.mortgageEdit(null)) },
                    onOpen = { id -> navController.navigate(Routes.mortgageEdit(id)) },
                )
            }
            composable(Routes.PAY) {
                EventListScreen(
                    kind = EventKind.PAY,
                    ui = ui,
                    onOpenMenu = { menuOpen = true },
                    onAdd = { navController.navigate(Routes.payEdit(null)) },
                    onOpen = { id -> navController.navigate(Routes.payEdit(id)) },
                )
            }
            composable(Routes.CARDS) {
                CardListScreen(
                    ui = ui,
                    onOpenMenu = { menuOpen = true },
                    onAdd = { navController.navigate(Routes.cardEdit(null)) },
                    onOpen = { id -> navController.navigate(Routes.cardEdit(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenMenu = { menuOpen = true },
                    onClearAll = { viewModel.clearAll() },
                )
            }
            composable(
                route = Routes.BILL_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                EventEditScreen(
                    kind = EventKind.BILL,
                    eventId = entry.arguments?.getString("id").toId(),
                    ui = ui,
                    onBack = { navController.popBackStack() },
                    onSave = { event, onDone -> viewModel.saveEvent(event, onDone) },
                    onDelete = { id, onDone -> viewModel.deleteEvent(id, onDone) },
                )
            }
            composable(
                route = Routes.MORTGAGE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                EventEditScreen(
                    kind = EventKind.MORTGAGE,
                    eventId = entry.arguments?.getString("id").toId(),
                    ui = ui,
                    onBack = { navController.popBackStack() },
                    onSave = { event, onDone -> viewModel.saveEvent(event, onDone) },
                    onDelete = { id, onDone -> viewModel.deleteEvent(id, onDone) },
                )
            }
            composable(
                route = Routes.PAY_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                EventEditScreen(
                    kind = EventKind.PAY,
                    eventId = entry.arguments?.getString("id").toId(),
                    ui = ui,
                    onBack = { navController.popBackStack() },
                    onSave = { event, onDone -> viewModel.saveEvent(event, onDone) },
                    onDelete = { id, onDone -> viewModel.deleteEvent(id, onDone) },
                )
            }
            composable(
                route = Routes.CARD_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                CardEditScreen(
                    cardId = entry.arguments?.getString("id").toId(),
                    ui = ui,
                    onBack = { navController.popBackStack() },
                    onSave = { card, onDone -> viewModel.saveCard(card, onDone) },
                    onDelete = { id, onDone -> viewModel.deleteCard(id, onDone) },
                )
            }
        }

        EndDrawer(
            open = menuOpen,
            currentRoute = backStack?.destination?.route,
            onClose = { menuOpen = false },
            onNavigate = { route ->
                menuOpen = false
                if (route == Routes.HOME) {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                } else {
                    navController.navigate(route) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            },
        )
    }
}

private fun String?.toId(): Long? {
    if (this == null || this == "new") return null
    return toLongOrNull()
}
