package com.keenin.calbudget.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackPopTest {
    @Test
    fun singleBackFromNestedReturnsToPrevious() {
        val stack = BackStack("home", "breakdown")
        assertTrue(stack.pressBack(owner = "breakdown"))
        assertEquals(listOf("home"), stack.entries)
    }

    @Test
    fun secondPressFromSameScreenDoesNotClearStartDestination() {
        val stack = BackStack("home", "breakdown")
        assertTrue(stack.pressBack(owner = "breakdown"))
        assertFalse(stack.pressBack(owner = "breakdown"))
        assertEquals(listOf("home"), stack.entries)
    }

    @Test
    fun backFromRootDoesNotPop() {
        val stack = BackStack("home")
        assertFalse(stack.pressBack(owner = "home"))
        assertEquals(listOf("home"), stack.entries)
    }

    @Test
    fun stalePressDoesNotSkipTheScreenUnderneath() {
        val stack = BackStack("home", "bills", "bills/edit/1")
        assertTrue(stack.pressBack(owner = "bills/edit/1"))
        assertFalse(stack.pressBack(owner = "bills/edit/1"))
        assertEquals(listOf("home", "bills"), stack.entries)
    }

    @Test
    fun rapidPressesOnTheCurrentScreenStopAtRoot() {
        val stack = BackStack("home", "breakdown", "bills/edit/4")
        val owners = listOf("bills/edit/4", "breakdown", "home", "home")
        val popped = owners.map { owner -> stack.pressBack(owner) }
        assertEquals(listOf(true, true, false, false), popped)
        assertEquals(listOf("home"), stack.entries)
    }

    @Test
    fun unguardedSecondPopIsTheBlankScreen() {
        val guarded = BackStack("home", "breakdown")
        guarded.pressBack(owner = "breakdown")
        guarded.pressBack(owner = "breakdown")

        val unguarded = mutableListOf("home", "breakdown")
        repeat(2) { unguarded.removeAt(unguarded.lastIndex) }

        assertEquals(listOf("home"), guarded.entries)
        assertEquals(emptyList<String>(), unguarded)
    }

    /**
     * Mirrors [popBackStackFrom]: a press is honored only for the destination
     * that still owns the back arrow, and never when it would empty the host.
     */
    private class BackStack(vararg start: String) {
        val entries = start.toMutableList()

        fun pressBack(owner: String): Boolean {
            if (!canPopOwnedDestination(
                    ownerId = owner,
                    currentId = entries.lastOrNull(),
                    hasPreviousDestination = entries.size > 1,
                )
            ) {
                return false
            }
            entries.removeAt(entries.lastIndex)
            return true
        }
    }
}
