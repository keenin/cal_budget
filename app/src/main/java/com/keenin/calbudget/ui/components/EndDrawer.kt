package com.keenin.calbudget.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.keenin.calbudget.ui.nav.Routes

private data class DrawerDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    DrawerDestination(Routes.HOME, "Home", Icons.Filled.Home),
    DrawerDestination(Routes.BILLS, "Bills", AppIcons.Receipt),
    DrawerDestination(Routes.CARDS, "Credit cards", AppIcons.CreditCard),
    DrawerDestination(Routes.MORTGAGE, "Mortgage / housing", AppIcons.AccountBalance),
    DrawerDestination(Routes.PAY, "Pay schedule", AppIcons.Payments),
    DrawerDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun EndDrawer(
    open: Boolean,
    currentRoute: String?,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    BackHandler(enabled = open, onBack = onClose)
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onClose),
            )
            val consume = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(312.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(interactionSource = consume, indication = null) {}
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Text(
                    "Cal Budget",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 4.dp),
                )
                Text(
                    "Until payday · next · following",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                destinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = routeMatches(currentRoute, destination.route),
                        icon = { Icon(destination.icon, contentDescription = null) },
                        onClick = { onNavigate(destination.route) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Local only · USD",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}

private fun routeMatches(current: String?, destination: String): Boolean {
    if (current == null) return false
    if (current == destination) return true
    return current.startsWith("$destination/")
}
