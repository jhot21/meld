package me.jhot.meld.tasker

import org.junit.Assert.*
import org.junit.Test

class ModeStateRunnerTest {

    @Test
    fun specificMode_active_directionActive_satisfied() {
        assertTrue(evaluateCondition(activeIds = setOf(1L, 2L), modeId = 1L, direction = StateDirection.ACTIVE))
    }

    @Test
    fun specificMode_notActive_directionActive_unsatisfied() {
        assertFalse(evaluateCondition(activeIds = setOf(2L), modeId = 1L, direction = StateDirection.ACTIVE))
    }

    @Test
    fun specificMode_active_directionInactive_unsatisfied() {
        assertFalse(evaluateCondition(activeIds = setOf(1L), modeId = 1L, direction = StateDirection.INACTIVE))
    }

    @Test
    fun specificMode_notActive_directionInactive_satisfied() {
        assertTrue(evaluateCondition(activeIds = setOf(2L), modeId = 1L, direction = StateDirection.INACTIVE))
    }

    @Test
    fun anyMode_someActive_directionActive_satisfied() {
        assertTrue(evaluateCondition(activeIds = setOf(3L), modeId = null, direction = StateDirection.ACTIVE))
    }

    @Test
    fun anyMode_noneActive_directionActive_unsatisfied() {
        assertFalse(evaluateCondition(activeIds = emptySet(), modeId = null, direction = StateDirection.ACTIVE))
    }

    @Test
    fun anyMode_noneActive_directionInactive_satisfied() {
        assertTrue(evaluateCondition(activeIds = emptySet(), modeId = null, direction = StateDirection.INACTIVE))
    }

    @Test
    fun anyMode_someActive_directionInactive_unsatisfied() {
        assertFalse(evaluateCondition(activeIds = setOf(3L), modeId = null, direction = StateDirection.INACTIVE))
    }
}
