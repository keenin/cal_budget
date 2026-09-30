package com.keenin.calbudget.ui.nav

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Pops [owner] only while it is still the current destination and a previous
 * destination is underneath it.
 *
 * The in-app back arrow stays composed for Navigation's exit transition, so a
 * second tap reaches this call after the first pop. An unguarded
 * [NavController.popBackStack] then removes the start destination and
 * [androidx.navigation.compose.NavHost] draws nothing.
 */
fun NavController.popBackStackFrom(owner: NavBackStackEntry): Boolean {
    if (!canPopOwnedDestination(
            ownerId = owner.id,
            currentId = currentBackStackEntry?.id,
            hasPreviousDestination = previousBackStackEntry != null,
        )
    ) {
        return false
    }
    return popBackStack()
}

internal fun canPopOwnedDestination(
    ownerId: String,
    currentId: String?,
    hasPreviousDestination: Boolean,
): Boolean = currentId != null && ownerId == currentId && hasPreviousDestination
