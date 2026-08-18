package me.jhot.meld.service

import me.jhot.meld.data.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBodyTest {

    private fun mode(id: Long, name: String, priority: Int, isDefault: Boolean = false) =
        Mode(id = id, name = name, priority = priority, isDefault = isDefault)

    @Test
    fun noModesActive_returnsOnlyDefaultMessage() {
        val modes = listOf(
            mode(1, "Home", 10) to false,
            mode(2, "Work", 20) to false,
        )
        assertEquals("Only default mode active", buildNotificationBody(modes))
    }

    @Test
    fun emptyList_returnsOnlyDefaultMessage() {
        assertEquals("Only default mode active", buildNotificationBody(emptyList()))
    }

    @Test
    fun singleModeActive_usesSingularLabel() {
        val modes = listOf(
            mode(1, "Home", 10) to true,
        )
        assertEquals("1 mode active: Home", buildNotificationBody(modes))
    }

    @Test
    fun twoModesActive_usesPluralLabel() {
        val modes = listOf(
            mode(1, "Home", 10) to true,
            mode(2, "Work", 20) to true,
        )
        assertEquals("2 modes active: Work, Home", buildNotificationBody(modes))
    }

    @Test
    fun activeModesOrderedByPriorityDescending() {
        val modes = listOf(
            mode(1, "Low", 5) to true,
            mode(2, "High", 50) to true,
            mode(3, "Mid", 25) to true,
        )
        assertEquals("3 modes active: High, Mid, Low", buildNotificationBody(modes))
    }

    @Test
    fun defaultModeExcluded_evenIfMarkedActive() {
        val modes = listOf(
            mode(1, "Default", 0, isDefault = true) to true,
            mode(2, "Work", 20) to true,
        )
        assertEquals("1 mode active: Work", buildNotificationBody(modes))
    }

    @Test
    fun inactiveModeNotIncluded() {
        val modes = listOf(
            mode(1, "Home", 10) to false,
            mode(2, "Work", 20) to true,
        )
        assertEquals("1 mode active: Work", buildNotificationBody(modes))
    }
}
