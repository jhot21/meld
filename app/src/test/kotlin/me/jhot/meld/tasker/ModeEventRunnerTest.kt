package me.jhot.meld.tasker

import org.junit.Assert.*
import org.junit.Test

class ModeEventRunnerTest {

    @Test
    fun specificFilter_matchingMode_satisfied() {
        assertTrue(matchesEventFilter(filterModeName = "Work", eventModeName = "Work"))
    }

    @Test
    fun specificFilter_differentMode_unsatisfied() {
        assertFalse(matchesEventFilter(filterModeName = "Work", eventModeName = "Home"))
    }

    @Test
    fun emptyFilter_anyMode_satisfiedForWork() {
        assertTrue(matchesEventFilter(filterModeName = "", eventModeName = "Work"))
    }

    @Test
    fun emptyFilter_anyMode_satisfiedForHome() {
        assertTrue(matchesEventFilter(filterModeName = "", eventModeName = "Home"))
    }
}
