package me.jhot.meld.tasker

import org.junit.Assert.*
import org.junit.Test

class TaskerBridgeTest {

    @Test
    fun diffSets_noChange_nothingAddedOrRemoved() {
        val (added, removed) = diffSets(previous = setOf(1L, 2L), current = setOf(1L, 2L))
        assertTrue(added.isEmpty())
        assertTrue(removed.isEmpty())
    }

    @Test
    fun diffSets_oneAdded() {
        val (added, removed) = diffSets(previous = setOf(1L), current = setOf(1L, 2L))
        assertEquals(setOf(2L), added)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun diffSets_oneRemoved() {
        val (added, removed) = diffSets(previous = setOf(1L, 2L), current = setOf(1L))
        assertTrue(added.isEmpty())
        assertEquals(setOf(2L), removed)
    }

    @Test
    fun diffSets_addedAndRemoved() {
        val (added, removed) = diffSets(previous = setOf(1L, 2L), current = setOf(2L, 3L))
        assertEquals(setOf(3L), added)
        assertEquals(setOf(1L), removed)
    }

    @Test
    fun diffSets_fromEmpty() {
        val (added, removed) = diffSets(previous = emptySet(), current = setOf(1L))
        assertEquals(setOf(1L), added)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun diffSets_toEmpty() {
        val (added, removed) = diffSets(previous = setOf(1L), current = emptySet())
        assertTrue(added.isEmpty())
        assertEquals(setOf(1L), removed)
    }
}
